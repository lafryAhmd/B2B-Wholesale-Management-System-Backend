package com.b2bwholesale.backend.modal;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "late_fee_rules")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LateFeeRule {

    @Id
    private Long id = 1L; // Singleton config row

    @Column(nullable = false)
    private boolean enabled = true;

    // PERCENTAGE or FIXED
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LateFeeType feeType = LateFeeType.PERCENTAGE;

    // Percentage (e.g. 2.5 means 2.5% of balance) OR fixed amount
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal feeAmount = new BigDecimal("2.00");

    // Days after due-date before fee is applied (grace period)
    @Column(nullable = false)
    private Integer graceDays = 0;

    // Max fee cap (0 = no cap). Only used for PERCENTAGE type.
    @Column(name = "max_fee_cap", precision = 12, scale = 2)
    private BigDecimal maxFeeCap = BigDecimal.ZERO;

    // If true, apply fee only once. If false, compound daily while overdue.
    @Column(name = "apply_once", nullable = false)
    private boolean applyOnce = true;

    @Column(name = "updated_by")
    private String updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
