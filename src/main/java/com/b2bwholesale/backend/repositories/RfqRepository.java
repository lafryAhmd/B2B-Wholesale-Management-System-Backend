package com.b2bwholesale.backend.repositories;

import com.b2bwholesale.backend.modal.Rfq;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RfqRepository extends JpaRepository<Rfq, Long> {

    List<Rfq> findByBuyerBusinessIdOrderByCreatedAtDesc(Long buyerBusinessId);

    List<Rfq> findBySellerBusinessIdOrderByCreatedAtDesc(Long sellerBusinessId);

    List<Rfq> findByProductIdOrderByCreatedAtDesc(Long productId);

    List<Rfq> findByStatus(String status);

    List<Rfq> findBySellerBusinessIdAndStatus(Long sellerBusinessId, String status);

    Optional<Rfq> findByRfqNumber(String rfqNumber);
}
