package com.b2bwholesale.backend.repositories;

import com.b2bwholesale.backend.modal.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByRegNumber(String regNumber);
    Optional<Customer> findByEmail(String email);
}
