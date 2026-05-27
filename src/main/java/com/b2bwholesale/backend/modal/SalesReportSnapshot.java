package com.b2bwholesale.backend.modal;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Audit trail of every sales report that was generated.
 * Lets admins see who ran what report and when, and caches the headline totals.
 */
@Entity
@Table(name = "sales_report_snapshots", indexes = {
        @Index(name = "idx_srs_generated_at", columnList = "generated_at"),
        @Index(name = "idx_srs_range", columnList = "start_date,end_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesReportSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "total_revenue", precision = 18, scale = 2, nullable = false)
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    @Column(name = "total_orders", nullable = false)
    private Integer totalOrders = 0;

    @Column(name = "avg_order_value", precision = 18, scale = 2, nullable = false)
    private BigDecimal avgOrderValue = BigDecimal.ZERO;

    @Column(name = "active_customers", nullable = false)
    private Integer activeCustomers = 0;

    @Column(name = "generated_by")
    private String generatedBy;

    @CreationTimestamp
    @Column(name = "generated_at", updatable = false)
    private LocalDateTime generatedAt;
}
