package com.b2bwholesale.backend.repositories;

import com.b2bwholesale.backend.modal.BulkPricing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BulkPricingRepository extends JpaRepository<BulkPricing, Long> {

    List<BulkPricing> findByProductIdAndIsActiveTrueOrderByMinQuantityAsc(Long productId);

    List<BulkPricing> findByProductIdOrderByMinQuantityAsc(Long productId);

    void deleteByProductId(Long productId);
}