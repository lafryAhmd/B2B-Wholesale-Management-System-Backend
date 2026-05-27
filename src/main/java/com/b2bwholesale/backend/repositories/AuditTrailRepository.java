package com.b2bwholesale.backend.repositories;

import com.b2bwholesale.backend.modal.AuditTrail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditTrailRepository extends JpaRepository<AuditTrail, Long> {

    List<AuditTrail> findByEntityTypeAndEntityId(String entityType, Long entityId);

    List<AuditTrail> findByEntityType(String entityType);

    List<AuditTrail> findByAction(String action);

    List<AuditTrail> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    List<AuditTrail> findAllByOrderByCreatedAtDesc();

    List<AuditTrail> findByActionAndCreatedAtBetween(String action, LocalDateTime start, LocalDateTime end);
}
