package com.b2bwholesale.backend.repositories;

import com.b2bwholesale.backend.modal.SalesReportSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalesReportSnapshotRepository extends JpaRepository<SalesReportSnapshot, Long> {
    List<SalesReportSnapshot> findTop20ByOrderByGeneratedAtDesc();
}
