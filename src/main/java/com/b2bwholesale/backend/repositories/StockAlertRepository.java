package com.b2bwholesale.backend.repositories;

import com.b2bwholesale.backend.modal.StockAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockAlertRepository extends JpaRepository<StockAlert, Long> {
    List<StockAlert> findAllByOrderByTriggeredAtDesc();
    Optional<StockAlert> findFirstByProductIdAndResolvedAtIsNullOrderByTriggeredAtDesc(Long productId);
}
