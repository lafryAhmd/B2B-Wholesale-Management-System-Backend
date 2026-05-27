package com.b2bwholesale.backend.scheduler;

import com.b2bwholesale.backend.services.LateFeeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OverdueInvoiceScheduler {

    private static final Logger log = LoggerFactory.getLogger(OverdueInvoiceScheduler.class);

    @Autowired
    private LateFeeService lateFeeService;

    /**
     * Run daily at 02:00 server time.
     * Scans all invoices past due date, marks them OVERDUE, applies late fees per rule.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void dailyOverdueScan() {
        log.info("[Scheduler] Starting daily overdue invoice scan...");
        try {
            int affected = lateFeeService.processOverdueAndLateFees("System (Scheduled)");
            log.info("[Scheduler] Overdue scan complete. {} invoice(s) affected.", affected);
        } catch (Exception e) {
            log.error("[Scheduler] Overdue scan failed", e);
        }
    }

    /**
     * Also run once at startup (after 60s) so fresh deployments catch up immediately.
     */
    @Scheduled(initialDelay = 60_000, fixedDelay = Long.MAX_VALUE)
    public void initialScanOnStartup() {
        log.info("[Scheduler] Running initial overdue scan after startup...");
        try {
            int affected = lateFeeService.processOverdueAndLateFees("System (Startup)");
            log.info("[Scheduler] Initial scan complete. {} invoice(s) affected.", affected);
        } catch (Exception e) {
            log.error("[Scheduler] Initial scan failed", e);
        }
    }
}
