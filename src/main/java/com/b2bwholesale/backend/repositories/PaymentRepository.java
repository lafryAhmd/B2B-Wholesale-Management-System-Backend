package com.b2bwholesale.backend.repositories;

import com.b2bwholesale.backend.modal.Payment;
import com.b2bwholesale.backend.modal.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentNumber(String paymentNumber);

    List<Payment> findByInvoiceId(Long invoiceId);

    List<Payment> findByStatus(PaymentStatus status);

    List<Payment> findByInvoiceIdAndStatus(Long invoiceId, PaymentStatus status);

    long countByStatus(PaymentStatus status);
}
