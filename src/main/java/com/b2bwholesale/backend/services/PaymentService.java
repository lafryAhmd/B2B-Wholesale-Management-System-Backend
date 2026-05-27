package com.b2bwholesale.backend.services;

import com.b2bwholesale.backend.modal.*;
import com.b2bwholesale.backend.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
public class PaymentService {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AuditTrailRepository auditTrailRepository;

    @Autowired
    private InvoiceSignatureService signatureService;

    // ─── Process Payment for an Invoice ─────────────────────────────────

    @Transactional
    public Payment processPayment(Long invoiceId, BigDecimal amount, PaymentMethod method,
                                  String referenceNumber, String notes, String processedBy) {

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceId));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new RuntimeException("Invoice is already fully paid.");
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new RuntimeException("Cannot pay a cancelled invoice.");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Payment amount must be greater than zero.");
        }
        if (amount.compareTo(invoice.getBalanceDue()) > 0) {
            throw new RuntimeException("Payment amount ($" + amount + ") exceeds balance due ($" + invoice.getBalanceDue() + ").");
        }

        // Create payment record
        Payment payment = new Payment();
        payment.setPaymentNumber(generatePaymentNumber());
        payment.setInvoice(invoice);
        payment.setAmount(amount);
        payment.setPaymentMethod(method);
        payment.setReferenceNumber(referenceNumber);
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaymentDate(LocalDateTime.now());
        payment.setNotes(notes);
        payment.setProcessedBy(processedBy != null ? processedBy : "System");

        Payment saved = paymentRepository.save(payment);

        // Update invoice
        String oldStatus = invoice.getStatus().name();
        invoice.setPaidAmount(invoice.getPaidAmount().add(amount));
        invoice.recalculateBalance();
        // Re-sign: paid/balance change may shift canonical fields
        signatureService.sign(invoice);
        invoiceRepository.save(invoice);

        // If invoice is fully paid, update order status to PROCESSING
        if (invoice.getStatus() == InvoiceStatus.PAID && invoice.getOrder() != null) {
            Order order = invoice.getOrder();
            if (order.getStatus() == OrderStatus.APPROVED) {
                order.setStatus(OrderStatus.PROCESSING);
                orderRepository.save(order);

                logAudit("ORDER_PROCESSING", "Order", order.getId(),
                        "Order " + order.getOrderNumber() + " moved to PROCESSING after full payment",
                        processedBy, amount, "APPROVED", "PROCESSING");
            }
        }

        // Audit trail for payment
        logAudit("PAYMENT_RECEIVED", "Payment", saved.getId(),
                "Payment " + saved.getPaymentNumber() + " of $" + amount + " received for Invoice " + invoice.getInvoiceNumber() +
                        " via " + method.name() + (referenceNumber != null ? " (Ref: " + referenceNumber + ")" : ""),
                processedBy, amount, oldStatus, invoice.getStatus().name());

        return saved;
    }

    // ─── Pay for Order directly (creates invoice if needed, then pays) ──

    @Transactional
    public Payment payForOrder(Long orderId, BigDecimal amount, PaymentMethod method,
                               String referenceNumber, String notes, String processedBy) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.APPROVED) {
            throw new RuntimeException("Can only pay for APPROVED orders. Current status: " + order.getStatus());
        }

        // Find or create invoice
        Invoice invoice = invoiceRepository.findByOrderId(orderId).orElse(null);
        if (invoice == null) {
            // Auto-generate invoice
            invoice = new Invoice();
            invoice.setInvoiceNumber(generateInvoiceNumber());
            invoice.setOrder(order);
            invoice.setBusiness(order.getBusiness());
            invoice.setSubtotal(order.getTotalAmount());
            invoice.setDiscountAmount(order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO);
            invoice.setTaxRate(BigDecimal.ZERO);
            invoice.setTaxAmount(BigDecimal.ZERO);

            BigDecimal totalAmount = order.getFinalAmount() != null ? order.getFinalAmount() : order.getTotalAmount();
            invoice.setTotalAmount(totalAmount);
            invoice.setPaidAmount(BigDecimal.ZERO);
            invoice.setBalanceDue(totalAmount);
            invoice.setStatus(InvoiceStatus.SENT);
            invoice.setDueDate(java.time.LocalDate.now().plusDays(30));
            invoice.setNotes("Auto-generated from Order #" + order.getOrderNumber());
            signatureService.sign(invoice);
            invoice = invoiceRepository.save(invoice);

            logAudit("INVOICE_CREATED", "Invoice", invoice.getId(),
                    "Invoice " + invoice.getInvoiceNumber() + " auto-generated for Order " + order.getOrderNumber(),
                    "System", totalAmount, null, "SENT");
        }

        // If no amount specified, pay full balance
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            amount = invoice.getBalanceDue();
        }

        return processPayment(invoice.getId(), amount, method, referenceNumber, notes, processedBy);
    }

    // ─── Record Payment (finance team) ──────────────────────────────────

    @Transactional
    public Payment recordPayment(Long invoiceId, BigDecimal amount, PaymentMethod method,
                                 String referenceNumber, String notes) {
        return processPayment(invoiceId, amount, method, referenceNumber, notes, "Finance");
    }

    // ─── Get Payments ───────────────────────────────────────────────────

    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    public Optional<Payment> getPaymentById(Long id) {
        return paymentRepository.findById(id);
    }

    public List<Payment> getPaymentsByInvoice(Long invoiceId) {
        return paymentRepository.findByInvoiceId(invoiceId);
    }

    // ─── Refund ─────────────────────────────────────────────────────────

    @Transactional
    public Payment refundPayment(Long paymentId, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new RuntimeException("Can only refund completed payments.");
        }

        // Mark payment as refunded
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setNotes((payment.getNotes() != null ? payment.getNotes() + "\n" : "") + "Refunded: " + reason);
        paymentRepository.save(payment);

        // Update invoice balance
        Invoice invoice = payment.getInvoice();
        String oldStatus = invoice.getStatus().name();
        invoice.setPaidAmount(invoice.getPaidAmount().subtract(payment.getAmount()));
        if (invoice.getPaidAmount().compareTo(BigDecimal.ZERO) < 0) {
            invoice.setPaidAmount(BigDecimal.ZERO);
        }
        invoice.setBalanceDue(invoice.getTotalAmount().subtract(invoice.getPaidAmount()));

        if (invoice.getPaidAmount().compareTo(BigDecimal.ZERO) == 0) {
            invoice.setStatus(InvoiceStatus.SENT);
            invoice.setPaidDate(null);
        } else {
            invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        }
        // Re-sign after refund mutation
        signatureService.sign(invoice);
        invoiceRepository.save(invoice);

        logAudit("PAYMENT_REFUNDED", "Payment", payment.getId(),
                "Payment " + payment.getPaymentNumber() + " of $" + payment.getAmount() + " refunded. Reason: " + reason,
                "Finance", payment.getAmount(), oldStatus, invoice.getStatus().name());

        return payment;
    }

    // ─── Audit Trail ────────────────────────────────────────────────────

    public List<AuditTrail> getAuditTrail() {
        return auditTrailRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<AuditTrail> getAuditTrailByEntity(String entityType, Long entityId) {
        return auditTrailRepository.findByEntityTypeAndEntityId(entityType, entityId);
    }

    public List<AuditTrail> getAuditTrailByDateRange(LocalDateTime start, LocalDateTime end) {
        return auditTrailRepository.findByCreatedAtBetween(start, end);
    }

    public List<AuditTrail> getAuditTrailByAction(String action) {
        return auditTrailRepository.findByAction(action);
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private String generatePaymentNumber() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long seq = System.currentTimeMillis() % 100000;
        int rand = new Random().nextInt(900) + 100;
        return "PAY-" + date + "-" + seq + "-" + rand;
    }

    private String generateInvoiceNumber() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long seq = System.currentTimeMillis() % 100000;
        int rand = new Random().nextInt(900) + 100;
        return "INV-" + date + "-" + seq + "-" + rand;
    }

    private void logAudit(String action, String entityType, Long entityId,
                          String description, String performedBy, BigDecimal amount,
                          String oldStatus, String newStatus) {
        AuditTrail audit = new AuditTrail();
        audit.setAction(action);
        audit.setEntityType(entityType);
        audit.setEntityId(entityId);
        audit.setDescription(description);
        audit.setPerformedBy(performedBy);
        audit.setAmount(amount);
        audit.setOldStatus(oldStatus);
        audit.setNewStatus(newStatus);
        auditTrailRepository.save(audit);
    }
}
