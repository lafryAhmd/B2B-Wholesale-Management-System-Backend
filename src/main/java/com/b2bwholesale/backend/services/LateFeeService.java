package com.b2bwholesale.backend.services;

import com.b2bwholesale.backend.modal.*;
import com.b2bwholesale.backend.repositories.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class LateFeeService {

    @Autowired
    private LateFeeRuleRepository ruleRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private AuditTrailRepository auditTrailRepository;

    @Autowired
    private InvoiceSignatureService signatureService;

    // Create default rule on startup if missing
    @PostConstruct
    public void initDefaultRule() {
        if (!ruleRepository.existsById(1L)) {
            LateFeeRule rule = new LateFeeRule();
            rule.setId(1L);
            rule.setEnabled(true);
            rule.setFeeType(LateFeeType.PERCENTAGE);
            rule.setFeeAmount(new BigDecimal("2.00"));
            rule.setGraceDays(0);
            rule.setMaxFeeCap(BigDecimal.ZERO);
            rule.setApplyOnce(true);
            rule.setUpdatedBy("System (default)");
            ruleRepository.save(rule);
        }
    }

    public LateFeeRule getRule() {
        return ruleRepository.findById(1L).orElseGet(() -> {
            initDefaultRule();
            return ruleRepository.findById(1L).orElseThrow();
        });
    }

    @Transactional
    public LateFeeRule updateRule(LateFeeRule updated, String updatedBy) {
        LateFeeRule rule = getRule();
        rule.setEnabled(updated.isEnabled());
        rule.setFeeType(updated.getFeeType());
        rule.setFeeAmount(updated.getFeeAmount() != null ? updated.getFeeAmount() : BigDecimal.ZERO);
        rule.setGraceDays(updated.getGraceDays() != null ? updated.getGraceDays() : 0);
        rule.setMaxFeeCap(updated.getMaxFeeCap() != null ? updated.getMaxFeeCap() : BigDecimal.ZERO);
        rule.setApplyOnce(updated.isApplyOnce());
        rule.setUpdatedBy(updatedBy != null ? updatedBy : "Admin");
        LateFeeRule saved = ruleRepository.save(rule);

        // Audit
        AuditTrail audit = new AuditTrail();
        audit.setAction("LATE_FEE_RULE_UPDATED");
        audit.setEntityType("LateFeeRule");
        audit.setEntityId(1L);
        audit.setDescription(String.format(
                "Late fee rule updated. Enabled=%s, Type=%s, Amount=%s, Grace=%s days, Cap=%s, ApplyOnce=%s",
                saved.isEnabled(), saved.getFeeType(), saved.getFeeAmount(),
                saved.getGraceDays(), saved.getMaxFeeCap(), saved.isApplyOnce()));
        audit.setPerformedBy(saved.getUpdatedBy());
        audit.setAmount(saved.getFeeAmount());
        audit.setNewStatus(saved.isEnabled() ? "ENABLED" : "DISABLED");
        auditTrailRepository.save(audit);

        return saved;
    }

    /**
     * Core job: find overdue invoices, mark status, apply late fee based on the active rule.
     * Called by the daily scheduler AND manually from admin endpoint.
     *
     * Returns number of invoices affected.
     */
    @Transactional
    public int processOverdueAndLateFees(String triggeredBy) {
        LateFeeRule rule = getRule();
        LocalDate today = LocalDate.now();
        List<Invoice> overdue = invoiceRepository.findOverdueInvoices(today);

        int affected = 0;
        for (Invoice inv : overdue) {
            boolean changed = false;

            // 1) Mark as OVERDUE if not already
            if (inv.getStatus() != InvoiceStatus.OVERDUE) {
                String oldStatus = inv.getStatus().name();
                inv.setStatus(InvoiceStatus.OVERDUE);
                changed = true;

                logAudit("INVOICE_OVERDUE", inv.getId(),
                        "Invoice " + inv.getInvoiceNumber() + " marked OVERDUE. Due: " + inv.getDueDate(),
                        triggeredBy, inv.getBalanceDue(), oldStatus, "OVERDUE");
            }

            // 2) Apply late fee if rule enabled & past grace
            if (rule.isEnabled() && inv.getDueDate() != null) {
                LocalDate feeEligibleFrom = inv.getDueDate().plusDays(rule.getGraceDays());
                if (!today.isBefore(feeEligibleFrom)) {

                    boolean shouldApply;
                    if (rule.isApplyOnce()) {
                        // Apply only if never applied before
                        shouldApply = (inv.getLastLateFeeAppliedAt() == null);
                    } else {
                        // Compound daily: apply if not applied today
                        shouldApply = (inv.getLastLateFeeAppliedAt() == null
                                || inv.getLastLateFeeAppliedAt().isBefore(today));
                    }

                    if (shouldApply) {
                        BigDecimal fee = calculateFee(rule, inv.getBalanceDue());

                        // Apply cap (for PERCENTAGE + max cap > 0 or compounding totals)
                        if (rule.getMaxFeeCap() != null
                                && rule.getMaxFeeCap().compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal existing = inv.getLateFee() != null ? inv.getLateFee() : BigDecimal.ZERO;
                            BigDecimal newTotal = existing.add(fee);
                            if (newTotal.compareTo(rule.getMaxFeeCap()) > 0) {
                                fee = rule.getMaxFeeCap().subtract(existing);
                                if (fee.compareTo(BigDecimal.ZERO) < 0) fee = BigDecimal.ZERO;
                            }
                        }

                        if (fee.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal existing = inv.getLateFee() != null ? inv.getLateFee() : BigDecimal.ZERO;
                            inv.setLateFee(existing.add(fee));
                            inv.setTotalAmount(inv.getTotalAmount().add(fee));
                            inv.setBalanceDue(inv.getBalanceDue().add(fee));
                            inv.setLastLateFeeAppliedAt(today);
                            changed = true;

                            logAudit("LATE_FEE_APPLIED", inv.getId(),
                                    String.format("Late fee of %s applied to invoice %s (rule: %s %s)",
                                            fee, inv.getInvoiceNumber(),
                                            rule.getFeeType(), rule.getFeeAmount()),
                                    triggeredBy, fee, null, "OVERDUE");
                        }
                    }
                }
            }

            if (changed) {
                // Re-sign: total/lateFee/balance changed, signature must reflect new canonical state
                signatureService.sign(inv);
                invoiceRepository.save(inv);
                affected++;
            }
        }

        // Summary audit (useful for admin dashboard alerts)
        if (affected > 0) {
            logAudit("OVERDUE_SCAN_COMPLETE", null,
                    "Daily overdue scan processed " + affected + " invoice(s). Total overdue: " + overdue.size(),
                    triggeredBy, null, null, null);
        }

        return affected;
    }

    private BigDecimal calculateFee(LateFeeRule rule, BigDecimal balance) {
        if (balance == null || balance.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        if (rule.getFeeType() == LateFeeType.PERCENTAGE) {
            return balance.multiply(rule.getFeeAmount())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            return rule.getFeeAmount().setScale(2, RoundingMode.HALF_UP);
        }
    }

    private void logAudit(String action, Long entityId, String description, String performedBy,
                          BigDecimal amount, String oldStatus, String newStatus) {
        AuditTrail audit = new AuditTrail();
        audit.setAction(action);
        audit.setEntityType("Invoice");
        audit.setEntityId(entityId);
        audit.setDescription(description);
        audit.setPerformedBy(performedBy != null ? performedBy : "System");
        audit.setAmount(amount);
        audit.setOldStatus(oldStatus);
        audit.setNewStatus(newStatus);
        auditTrailRepository.save(audit);
    }
}
