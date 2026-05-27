package com.b2bwholesale.backend.controller;

import com.b2bwholesale.backend.modal.Product;
import com.b2bwholesale.backend.modal.StockAlert;
import com.b2bwholesale.backend.modal.StockMovement;
import com.b2bwholesale.backend.repositories.ProductRepository;
import com.b2bwholesale.backend.repositories.StockAlertRepository;
import com.b2bwholesale.backend.repositories.StockMovementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    @Autowired private ProductRepository productRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private StockAlertRepository stockAlertRepository;

    @GetMapping
    public List<Map<String, Object>> getInventory(@RequestParam(required = false) Long businessId) {
        return productRepository.findByIsDeletedFalse().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .filter(p -> businessId == null
                        || (p.getBusiness() != null && businessId.equals(p.getBusiness().getId())))
                .map(this::toInventoryItem)
                .collect(Collectors.toList());
    }

    @GetMapping("/low-stock")
    public List<Map<String, Object>> getLowStock(@RequestParam(required = false) Long businessId) {
        return productRepository.findByIsDeletedFalse().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .filter(p -> businessId == null
                        || (p.getBusiness() != null && businessId.equals(p.getBusiness().getId())))
                .filter(p -> {
                    int stock = p.getStock() == null ? 0 : p.getStock();
                    int threshold = effectiveThreshold(p);
                    return stock <= threshold;
                })
                .map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("productId", p.getId());
                    m.put("productName", p.getName());
                    m.put("categoryName", p.getCategory() != null ? p.getCategory() : "General");
                    m.put("stock", p.getStock() == null ? 0 : p.getStock());
                    m.put("threshold", effectiveThreshold(p));
                    return m;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/alert-history")
    public List<Map<String, Object>> getAlertHistory(@RequestParam(required = false) Long businessId) {
        // Filter alerts by products belonging to this business
        Set<Long> myProductIds = businessId == null ? null :
                productRepository.findByIsDeletedFalse().stream()
                        .filter(p -> p.getBusiness() != null && businessId.equals(p.getBusiness().getId()))
                        .map(Product::getId).collect(Collectors.toSet());
        return stockAlertRepository.findAllByOrderByTriggeredAtDesc().stream()
                .filter(a -> myProductIds == null || myProductIds.contains(a.getProductId()))
                .map(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("productName", a.getProductName());
            m.put("stockAtAlert", a.getStockAtAlert());
            m.put("threshold", a.getThreshold());
            m.put("triggeredAt", a.getTriggeredAt());
            m.put("resolvedAt", a.getResolvedAt());
            return m;
        }).collect(Collectors.toList());
    }

    @PutMapping("/{id}/stock")
    public ResponseEntity<?> updateStock(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Optional<Product> opt = productRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Product p = opt.get();
        int delta = ((Number) body.getOrDefault("quantity", 0)).intValue();
        String note = body.get("note") == null ? "" : body.get("note").toString();
        String updatedBy = body.get("updatedBy") == null ? "System" : body.get("updatedBy").toString();

        int current = p.getStock() == null ? 0 : p.getStock();
        int newStock = Math.max(0, current + delta);
        p.setStock(newStock);
        p.setLastUpdatedBy(updatedBy);
        productRepository.save(p);

        StockMovement mv = new StockMovement();
        mv.setProductId(p.getId());
        mv.setQuantity(delta);
        mv.setStockAfter(newStock);
        mv.setNote(note);
        mv.setUpdatedBy(updatedBy);
        stockMovementRepository.save(mv);

        checkAndLogAlert(p);

        return ResponseEntity.ok(toInventoryItem(p));
    }

    @PutMapping("/{id}/threshold")
    public ResponseEntity<?> updateThreshold(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Optional<Product> opt = productRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Product p = opt.get();
        int threshold = ((Number) body.getOrDefault("threshold", 0)).intValue();
        p.setLowStockThreshold(threshold);
        productRepository.save(p);

        checkAndLogAlert(p);

        return ResponseEntity.ok(toInventoryItem(p));
    }

    private void checkAndLogAlert(Product p) {
        int stock = p.getStock() == null ? 0 : p.getStock();
        int threshold = effectiveThreshold(p);
        Optional<StockAlert> existing = stockAlertRepository
                .findFirstByProductIdAndResolvedAtIsNullOrderByTriggeredAtDesc(p.getId());

        if (stock <= threshold) {
            if (existing.isEmpty()) {
                StockAlert a = new StockAlert();
                a.setProductId(p.getId());
                a.setProductName(p.getName());
                a.setStockAtAlert(stock);
                a.setThreshold(threshold);
                a.setTriggeredAt(LocalDateTime.now());
                stockAlertRepository.save(a);
            }
        } else if (existing.isPresent()) {
            StockAlert a = existing.get();
            a.setResolvedAt(LocalDateTime.now());
            stockAlertRepository.save(a);
        }
    }

    private int effectiveThreshold(Product p) {
        if (p.getLowStockThreshold() != null) return p.getLowStockThreshold();
        return p.getMoq() != null ? p.getMoq() : 10;
    }

    private Map<String, Object> toInventoryItem(Product p) {
        int stock = p.getStock() == null ? 0 : p.getStock();
        int threshold = effectiveThreshold(p);
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("productName", p.getName());
        m.put("categoryName", p.getCategory() != null ? p.getCategory() : "General");
        m.put("stock", stock);
        m.put("lowStockThreshold", threshold);
        m.put("isLowStock", stock <= threshold);
        m.put("lastUpdatedAt", p.getUpdatedAt() != null ? p.getUpdatedAt() : p.getCreatedAt());
        m.put("lastUpdatedBy", p.getLastUpdatedBy() != null ? p.getLastUpdatedBy()
                : (p.getBusiness() != null ? p.getBusiness().getName() : "System"));
        m.put("sku", p.getSku());
        m.put("unit", p.getUnit() != null ? p.getUnit() : "piece");
        m.put("basePrice", p.getBasePrice());
        m.put("businessName", p.getBusiness() != null ? p.getBusiness().getName() : "Unknown");
        return m;
    }
}
