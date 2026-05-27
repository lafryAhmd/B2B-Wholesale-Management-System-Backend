package com.b2bwholesale.backend.controller;

import com.b2bwholesale.backend.modal.BulkPricing;
import com.b2bwholesale.backend.modal.Product;
import com.b2bwholesale.backend.repositories.BulkPricingRepository;
import com.b2bwholesale.backend.repositories.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@RestController
@RequestMapping("/api")
public class BulkPricingController {

    @Autowired
    private BulkPricingRepository bulkPricingRepository;

    @Autowired
    private ProductRepository productRepository;

    // GET all tiers for a product
    @GetMapping("/products/{productId}/pricing")
    public List<BulkPricing> getTiers(@PathVariable Long productId) {
        return bulkPricingRepository.findByProductIdOrderByMinQuantityAsc(productId);
    }

    // POST - Add new pricing tier
    @PostMapping("/products/{productId}/pricing")
    public ResponseEntity<?> addTier(@PathVariable Long productId,
                                     @RequestBody BulkPricing tier) {
        if (!productRepository.existsById(productId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Product not found"));
        }
        tier.setProductId(productId);
        BulkPricing saved = bulkPricingRepository.save(tier);
        return ResponseEntity.status(201).body(saved);
    }

    // PUT - Update pricing tier
    @PutMapping("/pricing/{tierId}")
    public ResponseEntity<?> updateTier(@PathVariable Long tierId,
                                        @RequestBody BulkPricing updated) {
        return bulkPricingRepository.findById(tierId)
                .map(tier -> {
                    tier.setMinQuantity(updated.getMinQuantity());
                    tier.setMaxQuantity(updated.getMaxQuantity());
                    tier.setDiscountPercent(updated.getDiscountPercent());
                    tier.setTierPrice(updated.getTierPrice());
                    tier.setIsActive(updated.getIsActive());
                    return ResponseEntity.ok().body((Object) bulkPricingRepository.save(tier));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // DELETE - Remove pricing tier
    @DeleteMapping("/pricing/{tierId}")
    public ResponseEntity<?> deleteTier(@PathVariable Long tierId) {
        if (bulkPricingRepository.existsById(tierId)) {
            bulkPricingRepository.deleteById(tierId);
            return ResponseEntity.ok(Map.of("message", "Tier deleted"));
        }
        return ResponseEntity.notFound().build();
    }

    // DYNAMIC PRICE CALCULATOR
    @GetMapping("/products/{productId}/calculate-price")
    public ResponseEntity<?> calculatePrice(
            @PathVariable Long productId,
            @RequestParam int quantity) {

        Optional<Product> optProduct = productRepository.findById(productId);
        if (optProduct.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Product not found"));
        }
        Product product = optProduct.get();

        // MOQ Validation
        if (quantity < product.getMoq()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Minimum order quantity is " + product.getMoq(),
                    "moq", product.getMoq()
            ));
        }

        // Stock Validation
        if (quantity > (product.getStock() != null ? product.getStock() : 0)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Only " + product.getStock() + " units in stock",
                    "availableStock", product.getStock()
            ));
        }

        // Get pricing tiers
        List<BulkPricing> tiers = bulkPricingRepository
                .findByProductIdAndIsActiveTrueOrderByMinQuantityAsc(productId);

        // Calculate price
        BigDecimal basePrice = product.getBasePrice();
        BigDecimal discountPercent = BigDecimal.ZERO;
        BigDecimal finalUnitPrice = basePrice;
        String appliedTier = "Base Price (No discount)";

        for (BulkPricing tier : tiers) {
            boolean inRange = quantity >= tier.getMinQuantity() &&
                    (tier.getMaxQuantity() == null || quantity <= tier.getMaxQuantity());
            if (inRange) {
                if (tier.getTierPrice() != null && tier.getTierPrice().compareTo(BigDecimal.ZERO) > 0) {
                    finalUnitPrice = tier.getTierPrice();
                    discountPercent = basePrice.subtract(tier.getTierPrice())
                            .divide(basePrice, 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"));
                    appliedTier = tier.getMinQuantity() + "+ units = Fixed LKR " + tier.getTierPrice();
                } else {
                    discountPercent = tier.getDiscountPercent();
                    finalUnitPrice = basePrice.multiply(
                            BigDecimal.ONE.subtract(discountPercent.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP))
                    ).setScale(2, RoundingMode.HALF_UP);
                    appliedTier = tier.getMinQuantity() + "+ units = " + discountPercent + "% discount";
                }
                break;
            }
        }

        BigDecimal totalAmount = finalUnitPrice.multiply(new BigDecimal(quantity)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalSavings = basePrice.subtract(finalUnitPrice).multiply(new BigDecimal(quantity)).setScale(2, RoundingMode.HALF_UP);

        // Build response
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("productId", productId);
        response.put("productName", product.getName());
        response.put("quantity", quantity);
        response.put("basePrice", basePrice);
        response.put("discountPercent", discountPercent.setScale(2, RoundingMode.HALF_UP));
        response.put("finalUnitPrice", finalUnitPrice);
        response.put("totalAmount", totalAmount);
        response.put("totalSavings", totalSavings);
        response.put("appliedTier", appliedTier);
        response.put("unit", product.getUnit());
        response.put("moq", product.getMoq());
        response.put("availableStock", product.getStock());

        // Include all tiers
        List<Map<String, Object>> tierList = new ArrayList<>();
        for (BulkPricing t : tiers) {
            Map<String, Object> tierMap = new LinkedHashMap<>();
            tierMap.put("id", t.getId());
            tierMap.put("minQuantity", t.getMinQuantity());
            tierMap.put("maxQuantity", t.getMaxQuantity());
            tierMap.put("discountPercent", t.getDiscountPercent());
            tierMap.put("tierPrice", t.getTierPrice());
            tierMap.put("isApplied", quantity >= t.getMinQuantity() &&
                    (t.getMaxQuantity() == null || quantity <= t.getMaxQuantity()));
            tierList.add(tierMap);
        }
        response.put("allTiers", tierList);

        return ResponseEntity.ok(response);
    }
}