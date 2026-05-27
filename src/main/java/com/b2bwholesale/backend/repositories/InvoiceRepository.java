package com.b2bwholesale.backend.repositories;

import com.b2bwholesale.backend.modal.Invoice;
import com.b2bwholesale.backend.modal.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    Optional<Invoice> findByOrderId(Long orderId);

    List<Invoice> findByBusinessId(Long businessId);

    List<Invoice> findByBuyerBusinessId(Long buyerBusinessId);

    List<Invoice> findByStatus(InvoiceStatus status);

    List<Invoice> findByBusinessIdAndStatus(Long businessId, InvoiceStatus status);

    List<Invoice> findByBuyerBusinessIdAndStatus(Long buyerBusinessId, InvoiceStatus status);

    @Query("SELECT i FROM Invoice i WHERE i.dueDate < :today AND i.status NOT IN ('PAID', 'CANCELLED')")
    List<Invoice> findOverdueInvoices(@Param("today") LocalDate today);

    @Query("SELECT i FROM Invoice i WHERE i.businessId = :businessId AND i.dueDate < :today AND i.status NOT IN ('PAID', 'CANCELLED')")
    List<Invoice> findOverdueByBusinessId(@Param("businessId") Long businessId, @Param("today") LocalDate today);

    long countByStatus(InvoiceStatus status);

    @Query("SELECT COALESCE(SUM(i.totalAmount), 0) FROM Invoice i WHERE i.status = 'PAID'")
    java.math.BigDecimal sumPaidAmount();

    @Query("SELECT COALESCE(SUM(i.balanceDue), 0) FROM Invoice i WHERE i.status NOT IN ('PAID', 'CANCELLED')")
    java.math.BigDecimal sumOutstandingBalance();
}
