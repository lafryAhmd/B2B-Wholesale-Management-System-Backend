package com.b2bwholesale.backend.repositories;

import com.b2bwholesale.backend.modal.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // Find all active, non-deleted products
    List<Product> findByIsDeletedFalseAndIsActiveTrue();

    // Find all non-deleted products (including inactive)
    List<Product> findByIsDeletedFalse();

    // Search by name
    List<Product> findByNameContainingIgnoreCaseAndIsDeletedFalse(String keyword);

    // Find by category
    List<Product> findByCategoryAndIsDeletedFalse(String category);

    // Find by SKU
    Optional<Product> findBySku(String sku);

    // Check if SKU exists
    boolean existsBySku(String sku);

    // Find products by Business
    List<Product> findByBusinessIdAndIsDeletedFalseAndIsActiveTrue(Long businessId);
}