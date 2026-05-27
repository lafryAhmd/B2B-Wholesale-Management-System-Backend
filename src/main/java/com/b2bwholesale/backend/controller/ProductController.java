package com.b2bwholesale.backend.controller;


import com.b2bwholesale.backend.modal.Product;
import com.b2bwholesale.backend.modal.ProductApprovalStatus;
import com.b2bwholesale.backend.repositories.BusinessRepository;
import com.b2bwholesale.backend.repositories.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BusinessRepository businessRepository;

    // ========================
    // CREATE - Add new product
    // New submissions are always PENDING until admin approves.
    // ========================
    @PostMapping
    public ResponseEntity<?> createProduct(@RequestBody Map<String, Object> payload) {
        String sku = (String) payload.get("sku");
        if (sku != null && productRepository.existsBySku(sku)) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "SKU already exists: " + sku);
            return ResponseEntity.badRequest().body(error);
        }

        Product product = new Product();
        product.setName((String) payload.get("name"));
        product.setDescription((String) payload.get("description"));
        product.setSku(sku);
        product.setBasePrice(new java.math.BigDecimal(payload.get("basePrice").toString()));
        product.setMoq(payload.get("moq") != null ? Integer.parseInt(payload.get("moq").toString()) : 1);
        product.setUnit((String) payload.get("unit"));
        product.setCategory((String) payload.get("category"));
        product.setStock(payload.get("stock") != null ? Integer.parseInt(payload.get("stock").toString()) : 0);
        product.setImageUrl((String) payload.get("imageUrl"));
        product.setImageUrl2((String) payload.get("imageUrl2"));
        if (payload.get("isActive") != null) {
            product.setIsActive(Boolean.parseBoolean(payload.get("isActive").toString()));
        }

        if (payload.get("businessId") != null) {
            Long businessId = Long.parseLong(payload.get("businessId").toString());
            businessRepository.findById(businessId).ifPresent(product::setBusiness);
        }

        // Force PENDING on create — businesses cannot self-approve.
        product.setApprovalStatus(ProductApprovalStatus.PENDING);

        Product saved = productRepository.save(product);
        return ResponseEntity.status(201).body(saved);
    }

    // ========================
    // READ - Get products
    //   Public (marketplace):   GET /api/products                  → only APPROVED
    //   Business's own view:    GET /api/products?businessId=123   → all statuses for that business
    //   Admin status filter:    GET /api/products?status=PENDING   → every business, that status
    // ========================
    @GetMapping
    public List<Product> getAllProducts(
            @RequestParam(required = false) Long businessId,
            @RequestParam(required = false) String status) {

        List<Product> all = productRepository.findByIsDeletedFalse();

        return all.stream()
                .filter(p -> {
                    if (businessId != null) {
                        // Owner viewing their own list — show all statuses.
                        return p.getBusiness() != null && businessId.equals(p.getBusiness().getId());
                    }
                    if (status != null) {
                        // Admin filtering by status.
                        try { return p.getApprovalStatus() == ProductApprovalStatus.valueOf(status.toUpperCase()); }
                        catch (Exception e) { return false; }
                    }
                    // Public marketplace — only approved & active.
                    return p.getApprovalStatus() == ProductApprovalStatus.APPROVED
                            && Boolean.TRUE.equals(p.getIsActive());
                })
                .collect(Collectors.toList());
    }

    // ========================
    // READ - single product
    // ========================
    @GetMapping("/{id}")
    public ResponseEntity<?> getProductById(@PathVariable Long id) {
        return productRepository.findById(id)
                .filter(p -> !p.getIsDeleted())
                .map(product -> ResponseEntity.ok().body((Object) product))
                .orElse(ResponseEntity.notFound().build());
    }

    // ========================
    // UPDATE - Edit product
    //   Editing a previously-rejected or approved product resets it to PENDING
    //   so the admin re-reviews material changes.
    // ========================
    @PutMapping("/{id}")
    public ResponseEntity<?> updateProduct(@PathVariable Long id,
                                           @RequestBody Product updated) {
        return productRepository.findById(id)
                .map(product -> {
                    product.setName(updated.getName());
                    product.setDescription(updated.getDescription());
                    product.setSku(updated.getSku());
                    product.setBasePrice(updated.getBasePrice());
                    product.setMoq(updated.getMoq());
                    product.setUnit(updated.getUnit());
                    product.setCategory(updated.getCategory());
                    product.setStock(updated.getStock());
                    product.setImageUrl(updated.getImageUrl());
                    product.setImageUrl2(updated.getImageUrl2());
                    product.setIsActive(updated.getIsActive());
                    // Force re-approval on edit
                    product.setApprovalStatus(ProductApprovalStatus.PENDING);
                    product.setRejectionReason(null);
                    Product saved = productRepository.save(product);
                    return ResponseEntity.ok().body((Object) saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ========================
    // DELETE - Soft delete
    // ========================
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id) {
        return productRepository.findById(id)
                .map(product -> {
                    product.setIsDeleted(true);
                    product.setIsActive(false);
                    productRepository.save(product);
                    Map<String, String> response = new HashMap<>();
                    response.put("message", "Product deleted successfully");
                    return ResponseEntity.ok().body((Object) response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ========================
    // ADMIN - Pending approval queue
    // ========================
    @GetMapping("/admin/pending")
    public List<Product> getPendingProducts() {
        return productRepository.findByIsDeletedFalse().stream()
                .filter(p -> p.getApprovalStatus() == ProductApprovalStatus.PENDING)
                .collect(Collectors.toList());
    }

    // ========================
    // ADMIN - Approve a product (makes it visible on the marketplace)
    // ========================
    @PutMapping("/{id}/approve")
    public ResponseEntity<?> approveProduct(@PathVariable Long id,
                                            @RequestBody(required = false) Map<String, Object> body) {
        return productRepository.findById(id)
                .map(product -> {
                    product.setApprovalStatus(ProductApprovalStatus.APPROVED);
                    product.setRejectionReason(null);
                    product.setReviewedBy(body != null && body.get("reviewedBy") != null
                            ? body.get("reviewedBy").toString() : "Admin");
                    product.setReviewedAt(LocalDateTime.now());
                    Product saved = productRepository.save(product);
                    return ResponseEntity.ok().body((Object) saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ========================
    // ADMIN - Reject a product (keeps it hidden, records a reason)
    // ========================
    @PutMapping("/{id}/reject")
    public ResponseEntity<?> rejectProduct(@PathVariable Long id,
                                           @RequestBody(required = false) Map<String, Object> body) {
        return productRepository.findById(id)
                .map(product -> {
                    product.setApprovalStatus(ProductApprovalStatus.REJECTED);
                    product.setRejectionReason(body != null && body.get("reason") != null
                            ? body.get("reason").toString() : "Not specified");
                    product.setReviewedBy(body != null && body.get("reviewedBy") != null
                            ? body.get("reviewedBy").toString() : "Admin");
                    product.setReviewedAt(LocalDateTime.now());
                    Product saved = productRepository.save(product);
                    return ResponseEntity.ok().body((Object) saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ========================
    // SEARCH - public (approved only)
    // ========================
    @GetMapping("/search")
    public List<Product> searchProducts(@RequestParam String keyword) {
        return productRepository.findByNameContainingIgnoreCaseAndIsDeletedFalse(keyword).stream()
                .filter(p -> p.getApprovalStatus() == ProductApprovalStatus.APPROVED)
                .collect(Collectors.toList());
    }

    // ========================
    // FILTER - By category (public, approved only)
    // ========================
    @GetMapping("/category/{category}")
    public List<Product> getByCategory(@PathVariable String category) {
        return productRepository.findByCategoryAndIsDeletedFalse(category).stream()
                .filter(p -> p.getApprovalStatus() == ProductApprovalStatus.APPROVED)
                .collect(Collectors.toList());
    }

    // ========================
    // TOGGLE - Active/Inactive
    // ========================
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<?> toggleProduct(@PathVariable Long id) {
        return productRepository.findById(id)
                .map(product -> {
                    product.setIsActive(!product.getIsActive());
                    Product saved = productRepository.save(product);
                    return ResponseEntity.ok().body((Object) saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ========================
    // FILTER - By business (owner view — all statuses)
    // ========================
    @GetMapping("/business/{businessId}")
    public List<Product> getByBusiness(@PathVariable Long businessId) {
        return productRepository.findByBusinessIdAndIsDeletedFalseAndIsActiveTrue(businessId);
    }

}
