package com.b2bwholesale.backend.controller;

import com.b2bwholesale.backend.modal.AuditTrail;
import com.b2bwholesale.backend.modal.Payment;
import com.b2bwholesale.backend.modal.PaymentMethod;
import com.b2bwholesale.backend.services.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    // ─── Pay for an invoice ─────────────────────────────────────────────
    @PostMapping("/invoices/{invoiceId}/pay")
    public ResponseEntity<?> payInvoice(@PathVariable Long invoiceId, @RequestBody Map<String, Object> body) {
        try {
            BigDecimal amount = new BigDecimal(body.getOrDefault("amount", "0").toString());
            PaymentMethod method = PaymentMethod.valueOf(
                    body.getOrDefault("paymentMethod", "CASH").toString().toUpperCase());
            String reference = (String) body.get("referenceNumber");
            String notes = (String) body.get("notes");
            String processedBy = (String) body.getOrDefault("processedBy", "Customer");

            Payment payment = paymentService.processPayment(invoiceId, amount, method, reference, notes, processedBy);
            return ResponseEntity.ok(payment);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Pay for order directly (auto-creates invoice) ──────────────────
    @PostMapping("/invoices/order/{orderId}/pay")
    public ResponseEntity<?> payForOrder(@PathVariable Long orderId, @RequestBody Map<String, Object> body) {
        try {
            BigDecimal amount = body.containsKey("amount") ?
                    new BigDecimal(body.get("amount").toString()) : null;
            PaymentMethod method = PaymentMethod.valueOf(
                    body.getOrDefault("paymentMethod", "CASH").toString().toUpperCase());
            String reference = (String) body.get("referenceNumber");
            String notes = (String) body.get("notes");
            String processedBy = (String) body.getOrDefault("processedBy", "Customer");

            Payment payment = paymentService.payForOrder(orderId, amount, method, reference, notes, processedBy);
            return ResponseEntity.ok(payment);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Record payment (finance team) ──────────────────────────────────
    @PostMapping("/invoices/{invoiceId}/record-payment")
    public ResponseEntity<?> recordPayment(@PathVariable Long invoiceId, @RequestBody Map<String, Object> body) {
        try {
            BigDecimal amount = new BigDecimal(body.getOrDefault("amount", "0").toString());
            PaymentMethod method = PaymentMethod.valueOf(
                    body.getOrDefault("paymentMethod", "CASH").toString().toUpperCase());
            String reference = (String) body.get("referenceNumber");
            String notes = (String) body.get("notes");

            Payment payment = paymentService.recordPayment(invoiceId, amount, method, reference, notes);
            return ResponseEntity.ok(payment);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Get all payments ───────────────────────────────────────────────
    @GetMapping("/payments")
    public ResponseEntity<List<Payment>> getAllPayments() {
        return ResponseEntity.ok(paymentService.getAllPayments());
    }

    // ─── Get payment by ID ──────────────────────────────────────────────
    @GetMapping("/payments/{id}")
    public ResponseEntity<?> getPaymentById(@PathVariable Long id) {
        return paymentService.getPaymentById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ─── Get payments for an invoice ────────────────────────────────────
    @GetMapping("/invoices/{invoiceId}/payments")
    public ResponseEntity<List<Payment>> getPaymentsByInvoice(@PathVariable Long invoiceId) {
        return ResponseEntity.ok(paymentService.getPaymentsByInvoice(invoiceId));
    }

    // ─── Refund payment ─────────────────────────────────────────────────
    @PostMapping("/payments/{paymentId}/refund")
    public ResponseEntity<?> refundPayment(@PathVariable Long paymentId, @RequestBody Map<String, String> body) {
        try {
            String reason = body.getOrDefault("reason", "No reason provided");
            Payment payment = paymentService.refundPayment(paymentId, reason);
            return ResponseEntity.ok(payment);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ─── Audit Trail ────────────────────────────────────────────────────
    @GetMapping("/invoices/audit-trail")
    public ResponseEntity<List<AuditTrail>> getAuditTrail(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        if (action != null && !action.isEmpty()) {
            return ResponseEntity.ok(paymentService.getAuditTrailByAction(action));
        }

        if (startDate != null && endDate != null) {
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                LocalDateTime start = LocalDateTime.parse(startDate + " 00:00:00", fmt);
                LocalDateTime end = LocalDateTime.parse(endDate + " 23:59:59", fmt);
                return ResponseEntity.ok(paymentService.getAuditTrailByDateRange(start, end));
            } catch (Exception e) {
                // Fall through to get all
            }
        }

        return ResponseEntity.ok(paymentService.getAuditTrail());
    }
}
