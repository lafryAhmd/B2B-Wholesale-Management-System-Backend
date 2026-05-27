package com.b2bwholesale.backend.controller;

import com.b2bwholesale.backend.modal.Admin;
import com.b2bwholesale.backend.modal.Business;
import com.b2bwholesale.backend.modal.OrderStatus;
import com.b2bwholesale.backend.repositories.AdminRepository;
import com.b2bwholesale.backend.repositories.BusinessRepository;
import com.b2bwholesale.backend.repositories.OrderRepository;
import com.b2bwholesale.backend.repositories.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired private AdminRepository adminRepository;
    @Autowired private BusinessRepository businessRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductRepository productRepository;

    @PostMapping("/login")
    public ResponseEntity<?> adminLogin(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String password = payload.get("password");

        if (email == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email and password are required"));
        }

        var found = adminRepository.findByEmail(email);

        if (found.isEmpty() || !password.equals(found.get().getPassword())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid admin credentials"));
        }

        Admin admin = found.get();
        Map<String, Object> response = new HashMap<>();
        response.put("id", admin.getId());
        response.put("name", admin.getName());
        response.put("email", admin.getEmail());
        response.put("role", "ADMIN");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/businesses")
    public List<Business> getAllBusinesses() {
        return businessRepository.findAll();
    }

    @GetMapping("/businesses/pending")
    public List<Business> getPendingBusinesses() {
        return businessRepository.findAll().stream()
                .filter(b -> "PENDING".equals(b.getStatus()))
                .toList();
    }

    @PutMapping("/businesses/{id}/approve")
    public ResponseEntity<?> approveBusiness(@PathVariable Long id) {
        return businessRepository.findById(id).map(b -> {
            b.setStatus("APPROVED");
            businessRepository.save(b);
            return ResponseEntity.ok(Map.of("message", "Business approved", "id", b.getId()));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/businesses/{id}/reject")
    public ResponseEntity<?> rejectBusiness(@PathVariable Long id) {
        return businessRepository.findById(id).map(b -> {
            b.setStatus("REJECTED");
            businessRepository.save(b);
            return ResponseEntity.ok(Map.of("message", "Business rejected", "id", b.getId()));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/dashboard/stats")
    public Map<String, Object> getDashboardStats() {
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);

        BigDecimal revenueThisMonth = orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrderStatus.APPROVED)
                .filter(o -> o.getOrderDate() != null && o.getOrderDate().isAfter(monthStart))
                .map(o -> o.getFinalAmount() == null ? BigDecimal.ZERO : o.getFinalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long activeListings = productRepository.findByIsDeletedFalse().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .count();

        long dealsInProgress = orderRepository.findByStatus(OrderStatus.PENDING_APPROVAL).size();

        int stockCleared = orderRepository.findAll().stream()
                .filter(o -> o.getStatus() == OrderStatus.APPROVED)
                .filter(o -> o.getOrderDate() != null && o.getOrderDate().isAfter(monthStart))
                .flatMap(o -> o.getItems().stream())
                .mapToInt(i -> i.getQuantity() == null ? 0 : i.getQuantity())
                .sum();

        long pendingBusinesses = businessRepository.findAll().stream()
                .filter(b -> "PENDING".equals(b.getStatus())).count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("revenueThisMonth", revenueThisMonth);
        stats.put("activeListings", activeListings);
        stats.put("dealsInProgress", dealsInProgress);
        stats.put("stockCleared", stockCleared);
        stats.put("pendingBusinesses", pendingBusinesses);
        stats.put("totalBusinesses", businessRepository.count());
        stats.put("totalProducts", productRepository.count());
        stats.put("totalOrders", orderRepository.count());
        return stats;
    }
}
