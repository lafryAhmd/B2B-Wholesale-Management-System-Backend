package com.b2bwholesale.backend.services;

import com.b2bwholesale.backend.modal.*;
import com.b2bwholesale.backend.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
public class InvoiceService {

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private AuditTrailRepository auditTrailRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private InvoiceSignatureService signatureService;

    // ─── Generate Invoice from Approved Order ───────────────────────────

    @Transactional
    public Invoice generateInvoiceFromOrder(Long orderId) {
        // Check if invoice already exists for this order
        Optional<Invoice> existing = invoiceRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            return existing.get();
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.APPROVED) {
            throw new RuntimeException("Can only generate invoice for APPROVED orders. Current status: " + order.getStatus());
        }

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(generateInvoiceNumber());
        invoice.setOrder(order);
        invoice.setBusiness(order.getBusiness());
        invoice.setSubtotal(order.getTotalAmount());
        invoice.setDiscountAmount(order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO);

        // Calculate tax (0% default - can be configured)
        BigDecimal taxRate = BigDecimal.ZERO;
        invoice.setTaxRate(taxRate);
        BigDecimal taxableAmount = order.getFinalAmount() != null ? order.getFinalAmount() : order.getTotalAmount();
        invoice.setTaxAmount(taxableAmount.multiply(taxRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));

        BigDecimal totalAmount = taxableAmount.add(invoice.getTaxAmount());
        invoice.setTotalAmount(totalAmount);
        invoice.setPaidAmount(BigDecimal.ZERO);
        invoice.setBalanceDue(totalAmount);
        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setDueDate(LocalDate.now().plusDays(30)); // Net 30 payment terms
        invoice.setNotes("Auto-generated from Order #" + order.getOrderNumber());

        // Set buyer business — match customer email → business email
        if (order.getCustomer() != null && order.getCustomer().getEmail() != null) {
            businessRepository.findByEmail(order.getCustomer().getEmail())
                    .ifPresent(buyerBiz -> invoice.setBuyerBusinessId(buyerBiz.getId()));
        }

        // Digital signature — sign before persisting so the signature reflects stored state
        signatureService.sign(invoice);

        Invoice saved = invoiceRepository.save(invoice);

        // Audit trail
        logAudit("INVOICE_CREATED", "Invoice", saved.getId(),
                "Invoice " + saved.getInvoiceNumber() + " created for Order " + order.getOrderNumber(),
                "System", saved.getTotalAmount(), null, "SENT");

        return saved;
    }

    // ─── Get Invoices ───────────────────────────────────────────────────

    public List<Invoice> getAllInvoices() {
        return invoiceRepository.findAll();
    }

    public Optional<Invoice> getInvoiceById(Long id) {
        return invoiceRepository.findById(id);
    }

    public Optional<Invoice> getInvoiceByOrderId(Long orderId) {
        return invoiceRepository.findByOrderId(orderId);
    }

    public List<Invoice> getInvoicesByBusiness(Long businessId) {
        return invoiceRepository.findByBusinessId(businessId);
    }

    public List<Invoice> getInvoicesByBuyer(Long buyerBusinessId) {
        return invoiceRepository.findByBuyerBusinessId(buyerBusinessId);
    }

    public List<Invoice> getInvoicesByStatus(InvoiceStatus status) {
        return invoiceRepository.findByStatus(status);
    }

    // ─── My Invoices (buyer side) ───────────────────────────────────────

    public List<Invoice> getMyInvoices(Long businessId) {
        // A business can be both seller and buyer
        // "My invoices" = invoices where I am the buyer OR seller
        List<Invoice> asSeller = invoiceRepository.findByBusinessId(businessId);
        List<Invoice> asBuyer = invoiceRepository.findByBuyerBusinessId(businessId);

        // Combine and deduplicate
        java.util.Set<Long> ids = new java.util.HashSet<>();
        List<Invoice> combined = new java.util.ArrayList<>();
        for (Invoice inv : asSeller) {
            if (ids.add(inv.getId())) combined.add(inv);
        }
        for (Invoice inv : asBuyer) {
            if (ids.add(inv.getId())) combined.add(inv);
        }
        return combined;
    }

    // ─── Overdue Invoices ───────────────────────────────────────────────

    public List<Invoice> getOverdueInvoices() {
        return invoiceRepository.findOverdueInvoices(LocalDate.now());
    }

    @Transactional
    public List<Invoice> checkAndMarkOverdue() {
        List<Invoice> overdue = invoiceRepository.findOverdueInvoices(LocalDate.now());
        for (Invoice inv : overdue) {
            if (inv.getStatus() != InvoiceStatus.OVERDUE) {
                String oldStatus = inv.getStatus().name();
                inv.setStatus(InvoiceStatus.OVERDUE);
                invoiceRepository.save(inv);

                logAudit("INVOICE_OVERDUE", "Invoice", inv.getId(),
                        "Invoice " + inv.getInvoiceNumber() + " marked as overdue. Due date: " + inv.getDueDate(),
                        "System", inv.getBalanceDue(), oldStatus, "OVERDUE");
            }
        }
        return overdue;
    }

    // ─── Cancel Invoice ─────────────────────────────────────────────────

    @Transactional
    public Invoice cancelInvoice(Long invoiceId, String reason) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceId));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new RuntimeException("Cannot cancel a fully paid invoice. Process a refund instead.");
        }

        String oldStatus = invoice.getStatus().name();
        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoice.setNotes((invoice.getNotes() != null ? invoice.getNotes() + "\n" : "") + "Cancelled: " + reason);
        Invoice saved = invoiceRepository.save(invoice);

        logAudit("INVOICE_CANCELLED", "Invoice", saved.getId(),
                "Invoice " + saved.getInvoiceNumber() + " cancelled. Reason: " + reason,
                "Admin", saved.getTotalAmount(), oldStatus, "CANCELLED");

        return saved;
    }

    // ─── Re-sign an invoice (rescue for legacy / unsigned rows) ─────────

    @Transactional
    public Invoice resignInvoice(Long invoiceId, String performedBy) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceId));
        signatureService.sign(invoice);
        Invoice saved = invoiceRepository.save(invoice);
        logAudit("INVOICE_RESIGNED", "Invoice", saved.getId(),
                "Invoice " + saved.getInvoiceNumber() + " re-signed.",
                performedBy != null ? performedBy : "Admin",
                saved.getTotalAmount(), null, saved.getStatus().name());
        return saved;
    }

    // ─── Invoice Stats ──────────────────────────────────────────────────

    public java.util.Map<String, Object> getInvoiceStats() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("totalInvoices", invoiceRepository.count());
        stats.put("paidCount", invoiceRepository.countByStatus(InvoiceStatus.PAID));
        stats.put("pendingCount", invoiceRepository.countByStatus(InvoiceStatus.SENT));
        stats.put("overdueCount", invoiceRepository.countByStatus(InvoiceStatus.OVERDUE));
        stats.put("totalPaid", invoiceRepository.sumPaidAmount());
        stats.put("totalOutstanding", invoiceRepository.sumOutstandingBalance());
        return stats;
    }

    // ─── Helpers ────────────────────────────────────────────────────────

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
