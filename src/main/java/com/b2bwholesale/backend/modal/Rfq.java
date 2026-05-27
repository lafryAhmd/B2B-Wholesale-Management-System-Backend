package com.b2bwholesale.backend.modal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "rfqs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Rfq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rfq_number", unique = true, nullable = false)
    private String rfqNumber;

    // Product being requested
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Product product;

    @Column(name = "product_id", insertable = false, updatable = false)
    private Long productId;

    // Buyer business (who is requesting the quote)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_business_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Business buyerBusiness;

    @Column(name = "buyer_business_id", insertable = false, updatable = false)
    private Long buyerBusinessId;

    // Seller business (who is providing the quote)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_business_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Business sellerBusiness;

    @Column(name = "seller_business_id", insertable = false, updatable = false)
    private Long sellerBusinessId;

    @Column(name = "requested_quantity", nullable = false)
    private Integer requestedQuantity;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    private String status = "PENDING";

    // Seller's quoted price per unit
    @Column(name = "offered_price", precision = 12, scale = 2)
    private BigDecimal offeredPrice;

    // Discount percentage offered by seller
    @Column(name = "offered_discount", precision = 5, scale = 2)
    private BigDecimal offeredDiscount = BigDecimal.ZERO;

    // Calculated total after discount
    @Column(name = "offered_total", precision = 12, scale = 2)
    private BigDecimal offeredTotal;

    @Column(name = "seller_notes", columnDefinition = "TEXT")
    private String sellerNotes;

    // Quote expiry date
    @Column(name = "valid_until")
    private LocalDate validUntil;

    // Linked order after acceptance
    @Column(name = "order_id")
    private Long orderId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // When the seller responded to the RFQ
    @Column(name = "responded_at")
    private LocalDateTime respondedAt;
}
