package com.b2bwholesale.backend.repositories;

import com.b2bwholesale.backend.modal.Business;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface BusinessRepository extends JpaRepository<Business, Long> {
    Optional<Business> findByEmail(String email);
    Optional<Business> findByUsername(String username);
}
