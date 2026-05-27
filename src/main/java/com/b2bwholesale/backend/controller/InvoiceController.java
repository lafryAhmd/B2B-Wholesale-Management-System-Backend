package com.b2bwholesale.backend.controller;

import com.b2bwholesale.backend.modal.Invoice;
import com.b2bwholesale.backend.modal.InvoiceStatus;
import com.b2bwholesale.backend.services.InvoiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private com.b2bwholesale.backend.services.LateFeeService lateFeeService;

    @Autowired
    private com.b2bwholesale.backend.services.InvoiceSignatureService signatureService;

    // ─── Get all invoices ───────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<List<Invoice>> getAllInvoices() {
        return ResponseEntity.ok(invoiceService.getAllInvoices());
    }

    // ─── Get invoice by ID ──────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<?> getInvoiceById(@PathVariable Long id) {
        return invoiceService.getInvoiceById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ─── Get invoice by order ID ────────────────────────────────────────
    @GetMapping("/order/{orderId}")
    public ResponseEntity<?> getInvoiceByOrderId(@PathVariable Long orderId) {
        return invoiceService.getInvoiceByOrderId(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ─── Generate invoice from approved order ───────────────────────────
    @PostMapping("/generate/{orderId}")
    public ResponseEntity<?> generateInvoice(@PathVariable Long orderId) {
        try {
            Invoice invoice = invoiceService.generateInvoiceFromOrder(orderId);
            return ResponseEntity.ok(invoice);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── My invoices (for a business) ───────────────────────────────────
    @GetMapping("/my")
    public ResponseEntity<List<Invoice>> getMyInvoices(@RequestParam Long businessId) {
        return ResponseEntity.ok(invoiceService.getMyInvoices(businessId));
    }

    // ─── Get invoices by seller business ────────────────────────────────
    @GetMapping("/business/{businessId}")
    public ResponseEntity<List<Invoice>> getByBusiness(@PathVariable Long businessId) {
        return ResponseEntity.ok(invoiceService.getInvoicesByBusiness(businessId));
    }

    // ─── Get invoices by buyer business ─────────────────────────────────
    @GetMapping("/buyer/{buyerBusinessId}")
    public ResponseEntity<List<Invoice>> getByBuyer(@PathVariable Long buyerBusinessId) {
        return ResponseEntity.ok(invoiceService.getInvoicesByBuyer(buyerBusinessId));
    }

    // ─── Get invoices by status ─────────────────────────────────────────
    @GetMapping("/status/{status}")
    public ResponseEntity<List<Invoice>> getByStatus(@PathVariable String status) {
        try {
            InvoiceStatus invoiceStatus = InvoiceStatus.valueOf(status.toUpperCase());
            return ResponseEntity.ok(invoiceService.getInvoicesByStatus(invoiceStatus));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ─── Check and mark overdue invoices (applies late fees per rule) ──
    @GetMapping("/overdue/check")
    public ResponseEntity<?> checkOverdue() {
        int affected = lateFeeService.processOverdueAndLateFees("Manual (API)");
        return ResponseEntity.ok(Map.of(
                "affected", affected,
                "invoices", invoiceService.getOverdueInvoices()
        ));
    }

    // ─── Get overdue invoices ───────────────────────────────────────────
    @GetMapping("/overdue")
    public ResponseEntity<List<Invoice>> getOverdue() {
        return ResponseEntity.ok(invoiceService.getOverdueInvoices());
    }

    // ─── Cancel invoice ─────────────────────────────────────────────────
    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelInvoice(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            String reason = body.getOrDefault("reason", "No reason provided");
            Invoice invoice = invoiceService.cancelInvoice(id, reason);
            return ResponseEntity.ok(invoice);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Verify invoice digital signature ───────────────────────────────
    @GetMapping("/{id}/verify")
    public ResponseEntity<?> verifySignature(@PathVariable Long id) {
        return invoiceService.getInvoiceById(id)
                .map(inv -> {
                    var result = signatureService.verify(inv);
                    return ResponseEntity.ok(Map.of(
                            "valid", result.isValid(),
                            "code", result.getCode(),
                            "message", result.getMessage(),
                            "invoiceNumber", inv.getInvoiceNumber(),
                            "signedAt", inv.getSignedAt(),
                            "signatureVersion", inv.getSignatureVersion()
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ─── Re-sign invoice (admin rescue for unsigned legacy invoices) ────
    @PostMapping("/{id}/sign")
    public ResponseEntity<?> signInvoice(@PathVariable Long id,
                                         @RequestParam(required = false) String performedBy) {
        try {
            return ResponseEntity.ok(invoiceService.resignInvoice(id, performedBy));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Invoice stats ──────────────────────────────────────────────────
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(invoiceService.getInvoiceStats());
    }
}
