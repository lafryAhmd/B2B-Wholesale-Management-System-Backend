package com.b2bwholesale.backend.repositories;

import com.b2bwholesale.backend.modal.Order;
import com.b2bwholesale.backend.modal.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByCustomerId(Long customerId);
    List<Order> findByBusinessId(Long businessId);
    List<Order> findByStatus(OrderStatus status);
    Optional<Order> findByOrderNumber(String orderNumber);
    long countByStatus(OrderStatus status);
    List<Order> findByCustomerIdOrderByOrderDateDesc(Long customerId);
}
