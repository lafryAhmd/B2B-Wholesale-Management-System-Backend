package com.b2bwholesale.backend.services;

import com.b2bwholesale.backend.modal.AuditTrail;
import com.b2bwholesale.backend.modal.Invoice;
import com.b2bwholesale.backend.repositories.AuditTrailRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Digital signature for invoices using HMAC-SHA256.
 *
 * Guarantees:
 *   - Integrity: any tampering with amount/dates/items invalidates the signature.
 *   - Authenticity: only the server (holding the secret key) can produce a valid signature.
 *
 * Payload = canonical pipe-delimited string of the financially-material fields.
 */
@Service
public class InvoiceSignatureService {

    @Value("${app.invoice.signing-secret}")
    private String secret;

    @Value("${app.invoice.signing-version:v1}")
    private String version;

    @Autowired
    private AuditTrailRepository auditTrailRepository;

    /**
     * Generate (or re-generate) signature for an invoice and stamp it.
     * Called whenever financially-material fields change (creation, payment, late-fee).
     */
    public void sign(Invoice invoice) {
        String payload = buildPayload(invoice);
        String sig = hmacSha256Hex(payload, secret);
        invoice.setSignature(sig);
        invoice.setSignatureVersion(version);
        invoice.setSignedAt(LocalDateTime.now());
    }

    /**
     * Verify integrity. Returns a result object the frontend can display.
     */
    public VerificationResult verify(Invoice invoice) {
        if (invoice.getSignature() == null || invoice.getSignature().isBlank()) {
            return new VerificationResult(false, "UNSIGNED",
                    "Invoice has no signature on record.");
        }

        String expected = hmacSha256Hex(buildPayload(invoice), secret);
        boolean match = constantTimeEquals(expected, invoice.getSignature());

        if (match) {
            return new VerificationResult(true, "VALID",
                    "Signature valid. Invoice contents have not been tampered with.");
        } else {
            // Log tampering attempt to audit trail
            AuditTrail audit = new AuditTrail();
            audit.setAction("INVOICE_SIGNATURE_INVALID");
            audit.setEntityType("Invoice");
            audit.setEntityId(invoice.getId());
            audit.setDescription("Invoice " + invoice.getInvoiceNumber()
                    + " failed signature verification. Possible tampering detected.");
            audit.setPerformedBy("System (Verifier)");
            audit.setAmount(invoice.getTotalAmount());
            audit.setNewStatus("SIGNATURE_INVALID");
            auditTrailRepository.save(audit);

            return new VerificationResult(false, "INVALID",
                    "Signature mismatch. Invoice contents may have been altered since issue.");
        }
    }

    // ─── Canonical payload builder ──────────────────────────────────────

    private String buildPayload(Invoice inv) {
        StringBuilder sb = new StringBuilder();
        sb.append(nullSafe(inv.getInvoiceNumber())).append("|");
        sb.append(nullSafe(inv.getOrderId())).append("|");
        sb.append(nullSafe(inv.getBusinessId())).append("|");
        sb.append(nullSafe(inv.getBuyerBusinessId())).append("|");
        sb.append(money(inv.getSubtotal())).append("|");
        sb.append(money(inv.getDiscountAmount())).append("|");
        sb.append(money(inv.getTaxAmount())).append("|");
        sb.append(money(inv.getLateFee())).append("|");
        sb.append(money(inv.getTotalAmount())).append("|");
        sb.append(nullSafe(inv.getDueDate())).append("|");
        sb.append(version);
        return sb.toString();
    }

    private String money(BigDecimal v) {
        return v == null ? "0.00" : v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String nullSafe(Object o) { return o == null ? "" : o.toString(); }

    // ─── HMAC-SHA256 ────────────────────────────────────────────────────

    private String hmacSha256Hex(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }

    // ─── Result DTO ─────────────────────────────────────────────────────

    public static class VerificationResult {
        public final boolean valid;
        public final String code;     // VALID | INVALID | UNSIGNED
        public final String message;

        public VerificationResult(boolean valid, String code, String message) {
            this.valid = valid;
            this.code = code;
            this.message = message;
        }

        public boolean isValid() { return valid; }
        public String getCode() { return code; }
        public String getMessage() { return message; }
    }
}
