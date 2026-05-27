package com.b2bwholesale.backend.repositories;

import com.b2bwholesale.backend.modal.LateFeeRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LateFeeRuleRepository extends JpaRepository<LateFeeRule, Long> {
}
