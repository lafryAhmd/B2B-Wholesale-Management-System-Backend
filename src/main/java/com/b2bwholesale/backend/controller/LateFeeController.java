package com.b2bwholesale.backend.controller;

import com.b2bwholesale.backend.modal.LateFeeRule;
import com.b2bwholesale.backend.services.LateFeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/late-fee")
public class LateFeeController {

    @Autowired
    private LateFeeService lateFeeService;

    // Get current late fee rule
    @GetMapping("/rule")
    public ResponseEntity<LateFeeRule> getRule() {
        return ResponseEntity.ok(lateFeeService.getRule());
    }

    // Update late fee rule (admin)
    @PutMapping("/rule")
    public ResponseEntity<?> updateRule(@RequestBody LateFeeRule updated,
                                        @RequestParam(required = false) String updatedBy) {
        try {
            LateFeeRule saved = lateFeeService.updateRule(updated, updatedBy);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Manually trigger overdue scan (admin)
    @PostMapping("/run-scan")
    public ResponseEntity<?> runOverdueScan(@RequestParam(required = false) String triggeredBy) {
        int affected = lateFeeService.processOverdueAndLateFees(
                triggeredBy != null ? triggeredBy : "Admin (Manual)");
        return ResponseEntity.ok(Map.of(
                "affected", affected,
                "message", "Overdue scan complete. " + affected + " invoice(s) updated."
        ));
    }
}
