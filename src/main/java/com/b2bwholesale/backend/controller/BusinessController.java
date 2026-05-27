package com.b2bwholesale.backend.controller;

import com.b2bwholesale.backend.modal.Business;
import com.b2bwholesale.backend.repositories.BusinessRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

@RestController
public class BusinessController {

    @Autowired
    private BusinessRepository businessRepository;

    @GetMapping("/api/businesses")
    public List<Business> getAllBusinesses() {
        return businessRepository.findAll();
    }

    @GetMapping("/api/businesses/{id}")
    public ResponseEntity<Business> getBusinessById(@PathVariable Long id) {
        return businessRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/business/login")
    public ResponseEntity<?> loginBusiness(@RequestBody Map<String, String> payload) {
        String username = payload.get("username"); // frontend sends email as username
        String password = payload.get("password");

        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email and password are required"));
        }

        // Try find by email first, then by username
        Optional<Business> found = businessRepository.findByEmail(username);
        if (found.isEmpty()) {
            found = businessRepository.findByUsername(username);
        }

        if (found.isEmpty() || !password.equals(found.get().getPassword())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid email or password"));
        }

        Business b = found.get();
        String status = b.getStatus() == null ? "PENDING" : b.getStatus();
        if ("PENDING".equals(status)) {
            return ResponseEntity.status(403).body(Map.of("error", "Your account is pending admin approval"));
        }
        if ("REJECTED".equals(status)) {
            return ResponseEntity.status(403).body(Map.of("error", "Your account has been rejected"));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", b.getId());
        response.put("email", b.getEmail());
        response.put("businessName", b.getName());
        response.put("businessType", b.getBusinessType());
        response.put("role", b.getRole() == null ? "BUSINESS" : b.getRole());
        response.put("status", status);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/business/register")
    public ResponseEntity<?> registerBusiness(@RequestBody Map<String, String> payload) {
        try {
            Business business = new Business();
            business.setName(payload.get("businessName"));
            business.setEmail(payload.get("email"));
            business.setPhone(payload.get("phone"));
            business.setBusinessType(payload.get("businessType"));
            business.setRegNumber(payload.get("registrationNumber"));
            business.setStreet(payload.get("street"));
            business.setCity(payload.get("city"));
            business.setZipCode(payload.get("zipCode"));
            business.setUsername(payload.get("username"));
            business.setPassword(payload.get("password"));

            Business saved = businessRepository.save(business);

            Map<String, Object> response = new HashMap<>();
            response.put("id", saved.getId());
            response.put("email", saved.getEmail());
            response.put("businessName", saved.getName());
            response.put("businessType", saved.getBusinessType());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Registration failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
