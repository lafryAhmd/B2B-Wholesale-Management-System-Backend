-- =====================================================================
-- Sales Report module — MySQL schema
-- =====================================================================
-- Hibernate (ddl-auto=update) will create this automatically from
-- SalesReportSnapshot.java on next startup. This file is for manual
-- provisioning, migrations, or reference.
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS sales_report_snapshots (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    start_date        DATE           NOT NULL,
    end_date          DATE           NOT NULL,
    total_revenue     DECIMAL(18,2)  NOT NULL DEFAULT 0.00,
    total_orders      INT            NOT NULL DEFAULT 0,
    avg_order_value   DECIMAL(18,2)  NOT NULL DEFAULT 0.00,
    active_customers  INT            NOT NULL DEFAULT 0,
    generated_by      VARCHAR(255)   NULL,
    generated_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_srs_generated_at (generated_at),
    KEY idx_srs_range (start_date, end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
-- Verification queries
-- =====================================================================
-- Show latest 20 runs:
--   SELECT * FROM sales_report_snapshots ORDER BY generated_at DESC LIMIT 20;
--
-- Total revenue across all persisted runs (sanity check):
--   SELECT SUM(total_revenue) FROM sales_report_snapshots;
--
-- Drop (if you ever need to reset):
--   DROP TABLE IF EXISTS sales_report_snapshots;
