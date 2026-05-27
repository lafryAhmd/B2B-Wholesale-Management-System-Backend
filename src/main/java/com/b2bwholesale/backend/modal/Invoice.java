package com.b2bwholesale.backend.modal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", unique = true, nullable = false)
    private String invoiceNumber;

    // The order this invoice is for
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @JsonIgnoreProperties({"items", "customer", "business", "hibernateLazyInitializer"})
    private Order order;

    @Column(name = "order_id", insertable = false, updatable = false)
    private Long orderId;

    // Seller business
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Business business;

    @Column(name = "business_id", insertable = false, updatable = false)
    private Long businessId;

    // Buyer business
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_business_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Business buyerBusiness;

    @Column(name = "buyer_business_id", insertable = false, updatable = false)
    private Long buyerBusinessId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", precision = 12, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "balance_due", precision = 12, scale = 2)
    private BigDecimal balanceDue = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "paid_date")
    private LocalDateTime paidDate;

    @Column(name = "late_fee", precision = 12, scale = 2)
    private BigDecimal lateFee = BigDecimal.ZERO;

    @Column(name = "last_late_fee_applied_at")
    private LocalDate lastLateFeeAppliedAt;

    // ─── Digital Signature (HMAC-SHA256 over canonical payload) ─────────
    @Column(name = "signature", length = 128)
    private String signature;

    @Column(name = "signature_version", length = 10)
    private String signatureVersion;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // Payments linked to this invoice
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties({"invoice", "hibernateLazyInitializer"})
    private List<Payment> payments = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Helper: recalculate balance
    public void recalculateBalance() {
        this.balanceDue = this.totalAmount.subtract(this.paidAmount);
        if (this.balanceDue.compareTo(BigDecimal.ZERO) <= 0) {
            this.balanceDue = BigDecimal.ZERO;
            this.status = InvoiceStatus.PAID;
            this.paidDate = LocalDateTime.now();
        } else if (this.paidAmount.compareTo(BigDecimal.ZERO) > 0) {
            this.status = InvoiceStatus.PARTIALLY_PAID;
        }
    }
}
