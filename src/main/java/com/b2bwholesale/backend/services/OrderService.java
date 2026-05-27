package com.b2bwholesale.backend.services;

import com.b2bwholesale.backend.modal.*;
import com.b2bwholesale.backend.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final BusinessRepository businessRepository;
    private final BulkPricingRepository bulkPricingRepository;
    private final InvoiceRepository invoiceRepository;
    private final AuditTrailRepository auditTrailRepository;

    private String generateOrderNumber() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // Use current timestamp millis + random to avoid duplicates across server restarts
        long seq = System.currentTimeMillis() % 1000000;
        long random = (long) (Math.random() * 9000) + 1000;
        return "ORD-" + datePart + "-" + seq + "-" + random;
    }

    @Transactional
    public Order createOrder(Long customerId, Long businessId, Long buyerBusinessId, String notes, List<OrderItem> items) {
        return createOrder(customerId, businessId, buyerBusinessId, notes, items, false);
    }

    @Transactional
    public Order createOrder(Long customerId, Long businessId, Long buyerBusinessId, String notes, List<OrderItem> items, boolean skipStockCheck) {
        Customer customer;

        // If buyerBusinessId is provided, auto-create/find customer from business
        if (buyerBusinessId != null) {
            Business buyerBiz = businessRepository.findById(buyerBusinessId)
                    .orElseThrow(() -> new RuntimeException("Buyer business not found"));
            customer = customerRepository.findByEmail(buyerBiz.getEmail())
                    .orElseGet(() -> {
                        Customer c = new Customer();
                        c.setName(buyerBiz.getName());
                        c.setEmail(buyerBiz.getEmail());
                        c.setPhone(buyerBiz.getPhone());
                        c.setRegNumber(buyerBiz.getRegNumber());
                        c.setCreditLimit(new java.math.BigDecimal("1000000"));
                        c.setRiskLevel(RiskLevel.LOW);
                        c.setIsActive(true);
                        return customerRepository.save(c);
                    });
        } else if (customerId != null) {
            customer = customerRepository.findById(customerId)
                    .orElseThrow(() -> new RuntimeException("Customer not found"));
        } else {
            throw new RuntimeException("Either customerId or buyerBusinessId must be provided");
        }

        // Determine seller business from the first item's product if businessId not provided
        Business business;
        if (businessId != null) {
            business = businessRepository.findById(businessId)
                    .orElseThrow(() -> new RuntimeException("Business not found"));
        } else {
            // Get seller business from the first product
            Product firstProduct = productRepository.findById(items.get(0).getProduct().getId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));
            business = firstProduct.getBusiness();
            if (business == null) {
                throw new RuntimeException("Product has no associated business");
            }
        }

        Order order = new Order();
        order.setOrderNumber(generateOrderNumber());
        order.setCustomer(customer);
        order.setBusiness(business);
        order.setNotes(notes);
        order.setStatus(OrderStatus.PENDING_APPROVAL);

        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;

        for (OrderItem item : items) {
            Product product = productRepository.findById(item.getProduct().getId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + item.getProduct().getId()));

            // MOQ Validation - block order if quantity below MOQ
            if (item.getQuantity() < product.getMoq()) {
                throw new RuntimeException(
                        "Quantity for '" + product.getName() + "' is " + item.getQuantity()
                                + " but minimum order quantity (MOQ) is " + product.getMoq()
                );
            }

            // Stock validation warning (skipped for RFQ-accepted orders — seller already committed via quote)
            if (!skipStockCheck && item.getQuantity() > product.getStock()) {
                throw new RuntimeException(
                        "Insufficient stock for '" + product.getName()
                                + "'. Requested: " + item.getQuantity()
                                + ", Available: " + product.getStock()
                );
            }

            item.setUnitPrice(product.getBasePrice());
            item.setProduct(product);

            // Apply bulk pricing discount if available
            BigDecimal discountPercent = BigDecimal.ZERO;
            BigDecimal tierPrice = null;
            List<BulkPricing> tiers = bulkPricingRepository
                    .findByProductIdAndIsActiveTrueOrderByMinQuantityAsc(product.getId());
            for (BulkPricing tier : tiers) {
                boolean aboveMin = item.getQuantity() >= tier.getMinQuantity();
                boolean belowMax = tier.getMaxQuantity() == null || item.getQuantity() <= tier.getMaxQuantity();
                if (aboveMin && belowMax) {
                    discountPercent = tier.getDiscountPercent();
                    tierPrice = tier.getTierPrice();
                    break;
                }
            }

            item.setDiscountPercent(discountPercent);

            BigDecimal effectiveUnitPrice = product.getBasePrice();
            if (tierPrice != null && tierPrice.compareTo(BigDecimal.ZERO) > 0) {
                effectiveUnitPrice = tierPrice;
                item.setUnitPrice(tierPrice);
            }

            BigDecimal lineGross = product.getBasePrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            BigDecimal lineNet = effectiveUnitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
            BigDecimal lineDiscountAmount = lineGross.subtract(lineNet);
            if (lineDiscountAmount.compareTo(BigDecimal.ZERO) < 0) {
                lineDiscountAmount = lineGross.multiply(discountPercent)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                lineNet = lineGross.subtract(lineDiscountAmount);
            }

            item.setLineTotal(lineNet);
            totalAmount = totalAmount.add(lineGross);
            totalDiscount = totalDiscount.add(lineDiscountAmount);

            order.addItem(item);
        }

        order.setTotalAmount(totalAmount);
        order.setDiscountAmount(totalDiscount);
        order.setFinalAmount(totalAmount.subtract(totalDiscount));

        // All orders start as PENDING_APPROVAL - must go through approval page first
        Order savedOrder = orderRepository.save(order);

        // Auto-Approval Engine: check if order qualifies for auto-approval
        // RFQ-accepted orders (skipStockCheck=true) are auto-approved unconditionally —
        // seller already committed supply & price via quote, so the order should flow straight to payment.
        if (skipStockCheck) {
            savedOrder.setApprovalType("AUTO");
            savedOrder.setAutoApprovalReason("Auto-approved from accepted RFQ quote — proceeding to payment");
            executeApproval(savedOrder, true);
        } else {
            autoApproveEngine(savedOrder);
        }

        return orderRepository.save(savedOrder);
    }

    private void autoApproveEngine(Order order) {
        if (order.getStatus() != OrderStatus.PENDING_APPROVAL) return;

        Customer customer = order.getCustomer();

        // Condition 1: Customer Risk Level must be LOW
        if (customer.getRiskLevel() != RiskLevel.LOW) {
            order.setAutoApprovalReason("Customer risk level is " + customer.getRiskLevel() + " - requires manual approval");
            return;
        }

        // Condition 2: Order amount must be within customer's credit limit
        if (order.getFinalAmount().compareTo(customer.getCreditLimit()) > 0) {
            order.setAutoApprovalReason("Order amount exceeds credit limit - requires manual approval");
            return;
        }

        // Condition 3: Stock must be available for all items
        for (OrderItem item : order.getItems()) {
            if (item.getProduct().getStock() < item.getQuantity()) {
                order.setAutoApprovalReason("Insufficient stock for " + item.getProduct().getName() + " - requires manual approval");
                return;
            }
        }

        // All conditions met - auto approve
        order.setApprovalType("AUTO");
        order.setAutoApprovalReason("Low risk customer, within credit limit, stock available");
        executeApproval(order);
    }

    @Transactional
    public Order modifyOrder(Long orderId, String notes, List<OrderItem> newItems) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (order.getStatus() != OrderStatus.PENDING_APPROVAL) {
            throw new RuntimeException("Only PENDING_APPROVAL orders can be modified.");
        }

        order.getItems().clear();
        if (notes != null) {
            order.setNotes(notes);
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;

        for (OrderItem item : newItems) {
            Product product = productRepository.findById(item.getProduct().getId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            // MOQ Validation
            if (item.getQuantity() < product.getMoq()) {
                throw new RuntimeException(
                        "Quantity for '" + product.getName() + "' is " + item.getQuantity()
                                + " but minimum order quantity (MOQ) is " + product.getMoq()
                );
            }

            item.setUnitPrice(product.getBasePrice());
            item.setProduct(product);

            BigDecimal discountPercent = BigDecimal.ZERO;
            BigDecimal tierPrice = null;
            List<BulkPricing> tiers = bulkPricingRepository
                    .findByProductIdAndIsActiveTrueOrderByMinQuantityAsc(product.getId());
            for (BulkPricing tier : tiers) {
                boolean aboveMin = item.getQuantity() >= tier.getMinQuantity();
                boolean belowMax = tier.getMaxQuantity() == null || item.getQuantity() <= tier.getMaxQuantity();
                if (aboveMin && belowMax) {
                    discountPercent = tier.getDiscountPercent();
                    tierPrice = tier.getTierPrice();
                    break;
                }
            }
            item.setDiscountPercent(discountPercent);

            BigDecimal effectiveUnitPrice = product.getBasePrice();
            if (tierPrice != null && tierPrice.compareTo(BigDecimal.ZERO) > 0) {
                effectiveUnitPrice = tierPrice;
                item.setUnitPrice(tierPrice);
            }

            BigDecimal lineGross = product.getBasePrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            BigDecimal lineNet = effectiveUnitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
            BigDecimal lineDiscountAmount = lineGross.subtract(lineNet);
            if (lineDiscountAmount.compareTo(BigDecimal.ZERO) < 0) {
                lineDiscountAmount = lineGross.multiply(discountPercent)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                lineNet = lineGross.subtract(lineDiscountAmount);
            }

            item.setLineTotal(lineNet);
            totalAmount = totalAmount.add(lineGross);
            totalDiscount = totalDiscount.add(lineDiscountAmount);
            order.addItem(item);
        }

        order.setTotalAmount(totalAmount);
        order.setDiscountAmount(totalDiscount);
        order.setFinalAmount(totalAmount.subtract(totalDiscount));
        order.setStatus(OrderStatus.PENDING_APPROVAL);
        order.setApprovalType(null);
        order.setAutoApprovalReason(null);
        order.setRejectionReason(null);

        autoApproveEngine(order);

        return orderRepository.save(order);
    }

    @Transactional
    public Order adminApproveOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (order.getStatus() == OrderStatus.APPROVED) {
            return order;
        }

        if (order.getStatus() == OrderStatus.REJECTED) {
            throw new RuntimeException("Cannot approve a rejected order");
        }

        order.setApprovalType("MANUAL");
        order.setAutoApprovalReason("Approved by admin");
        order.setRejectionReason(null);
        executeApproval(order);
        return orderRepository.save(order);
    }

    @Transactional
    public Order rejectOrder(Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (order.getStatus() == OrderStatus.APPROVED) {
            throw new RuntimeException("Cannot reject an already approved order");
        }

        order.setStatus(OrderStatus.REJECTED);
        order.setApprovalType("MANUAL");
        order.setRejectionReason(reason != null ? reason : "Rejected by admin");
        return orderRepository.save(order);
    }

    private void executeApproval(Order order) {
        executeApproval(order, false);
    }

    private void executeApproval(Order order, boolean skipStockCheck) {
        // Stock reduction happens on approval
        for (OrderItem item : order.getItems()) {
            Product p = item.getProduct();
            if (p.getStock() < item.getQuantity()) {
                if (!skipStockCheck) {
                    throw new RuntimeException("Insufficient stock for product: " + p.getName());
                }
                // RFQ path — deduct available stock, remainder is treated as backorder (seller commitment)
                p.setStock(0);
            } else {
                p.setStock(p.getStock() - item.getQuantity());
            }
            productRepository.save(p);
        }
        order.setStatus(OrderStatus.APPROVED);

        // Auto-generate invoice on approval
        generateInvoiceForOrder(order);
    }

    private void generateInvoiceForOrder(Order order) {
        // Check if invoice already exists
        if (invoiceRepository.findByOrderId(order.getId()).isPresent()) {
            return;
        }

        Invoice invoice = new Invoice();
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
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice.setNotes("Auto-generated from Order #" + order.getOrderNumber());

        Invoice saved = invoiceRepository.save(invoice);

        // Audit trail
        AuditTrail audit = new AuditTrail();
        audit.setAction("INVOICE_CREATED");
        audit.setEntityType("Invoice");
        audit.setEntityId(saved.getId());
        audit.setDescription("Invoice " + saved.getInvoiceNumber() + " auto-generated on order approval for Order " + order.getOrderNumber());
        audit.setPerformedBy("System");
        audit.setAmount(totalAmount);
        audit.setNewStatus("SENT");
        auditTrailRepository.save(audit);
    }

    private String generateInvoiceNumber() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long seq = System.currentTimeMillis() % 100000;
        int rand = new Random().nextInt(900) + 100;
        return "INV-" + date + "-" + seq + "-" + rand;
    }
}
