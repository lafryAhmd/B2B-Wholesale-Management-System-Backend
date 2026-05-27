package com.b2bwholesale.backend.controller;

import com.b2bwholesale.backend.modal.Rfq;
import com.b2bwholesale.backend.services.RfqService;
import com.b2bwholesale.backend.services.RfqService.RfqRequest;
import com.b2bwholesale.backend.services.RfqService.RfqResponseRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rfqs")
public class RfqController {

    @Autowired
    private RfqService rfqService;

    @PostMapping
    public ResponseEntity<?> createRfq(@RequestBody RfqRequest request) {
        try {
            Rfq rfq = rfqService.createRfq(request);
            return ResponseEntity.ok(rfq);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping
    public List<Rfq> getAll() {
        return rfqService.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        try {
            Rfq rfq = rfqService.getById(id);
            return ResponseEntity.ok(rfq);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/buyer/{buyerBusinessId}")
    public List<Rfq> getByBuyer(@PathVariable Long buyerBusinessId) {
        return rfqService.getByBuyer(buyerBusinessId);
    }

    @GetMapping("/seller/{sellerBusinessId}")
    public List<Rfq> getBySeller(@PathVariable Long sellerBusinessId) {
        return rfqService.getBySeller(sellerBusinessId);
    }

    @GetMapping("/seller/{sellerBusinessId}/pending")
    public List<Rfq> getPendingBySeller(@PathVariable Long sellerBusinessId) {
        return rfqService.getPendingBySeller(sellerBusinessId);
    }

    @PutMapping("/{id}/respond")
    public ResponseEntity<?> respondToRfq(@PathVariable Long id, @RequestBody RfqResponseRequest request) {
        try {
            Rfq rfq = rfqService.respondToRfq(id, request);
            return ResponseEntity.ok(rfq);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/{id}/accept")
    public ResponseEntity<?> acceptQuote(@PathVariable Long id) {
        try {
            Rfq rfq = rfqService.acceptQuote(id);
            return ResponseEntity.ok(rfq);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<?> rejectQuote(@PathVariable Long id) {
        try {
            Rfq rfq = rfqService.rejectQuote(id);
            return ResponseEntity.ok(rfq);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
