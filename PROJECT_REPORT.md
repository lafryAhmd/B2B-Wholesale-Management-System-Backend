# B2B Wholesale Management System — Project Report

**Student:** Yashodha Dishan
**Date:** 22 April 2026
**Stack:** Spring Boot 3.4.3 · Java 21 · MySQL 8.0 · React 18 · Vite
**Backend port:** 8081  ·  **Frontend port:** 3000

---

## 1. Executive Summary

The B2B Wholesale Management System is a multi-tenant web platform that lets
**wholesale businesses** register, list products, manage stock, and fulfil
orders from **customer buyers**, while a **super-admin** approves new
businesses and monitors sales across the whole marketplace.

The system is organised around **six core functions**, each implemented as a
dedicated Spring Boot module (entity + repository + controller + React page).

| # | Function                     | Primary Role | Key Endpoint                  |
|---|------------------------------|--------------|--------------------------------|
| 1 | Business Registration & Admin Approval | Admin    | `/api/admin/approvals`        |
| 2 | Product Management           | Business     | `/api/products`               |
| 3 | Order Management             | Customer / Business | `/api/orders`          |
| 4 | Inventory & Stock Alerts     | Business     | `/api/inventory`              |
| 5 | Invoice & Payment            | Customer / Business | `/api/invoices`         |
| 6 | Sales Report (Analytics)     | Admin        | `/api/reports/sales`          |

---

## 2. Architecture Overview

```
┌──────────────────┐   HTTP/JSON   ┌────────────────────┐   JDBC   ┌──────────┐
│ React + Vite     │ ────────────▶ │ Spring Boot 3.4.3  │ ───────▶ │ MySQL 8  │
│ (port 3000)      │ ◀──────────── │ REST API (port 8081)│ ◀─────── │ b2b_wholesale │
└──────────────────┘               └────────────────────┘          └──────────┘
```

- **Role-based access:** `ADMIN` vs `BUSINESS` stored in `localStorage.user`.
  React guards block direct URL access; Spring endpoints filter by `businessId`.
- **Multi-tenancy:** every product/order/inventory row carries a `business_id`
  FK; API calls pass `?businessId=...` so one company never sees another's data.
- **Persistence:** JPA / Hibernate with `ddl-auto=update` — schema evolves
  automatically from entity classes.

---

## 3. The Six Core Functions

### 3.1 Function 1 — Business Registration & Admin Approval

**Purpose:** New wholesale companies sign up; a super-admin reviews and
approves/rejects each account before they can trade.

| Layer      | Artifact                                   |
|------------|--------------------------------------------|
| Entity     | `Business` (fields: `name`, `email`, `status ∈ {PENDING, APPROVED, REJECTED}`) |
| Repository | `BusinessRepository`                       |
| Controller | `AdminController`, `BusinessController`    |
| Frontend   | `RegisterPage.jsx`, `AdminApprovalsPage.jsx` |

**Key endpoints**
- `POST /api/business/register` — create PENDING business
- `GET  /api/admin/approvals` — list pending businesses
- `PUT  /api/admin/approvals/{id}/approve`
- `PUT  /api/admin/approvals/{id}/reject`

**Flow**
1. Company fills registration form → row inserted with `status=PENDING`.
2. Admin sees it on `/admin/approvals`; clicks Approve.
3. Status flips to `APPROVED`; company can now log in and access their dashboard.

---

### 3.2 Function 2 — Product Management

**Purpose:** An approved business can add, edit, and remove the products they
sell wholesale.

| Layer      | Artifact                       |
|------------|--------------------------------|
| Entity     | `Product` (FK `business_id`)   |
| Repository | `ProductRepository`            |
| Controller | `ProductController`            |
| Frontend   | `ProductManagement.jsx`        |

**Key endpoints**
- `GET    /api/products`            — list all (marketplace view)
- `GET    /api/products?businessId=X` — list one business's catalogue
- `POST   /api/products`            — create (payload includes `businessId`)
- `PUT    /api/products/{id}`
- `DELETE /api/products/{id}` (soft-delete: sets `is_deleted=true`)

**Data isolation:** Every product row carries `business_id`; buyers on the
marketplace see every product, but owners only manage their own via the
`businessId` filter.

---

### 3.3 Function 3 — Order Management

**Purpose:** Customers place bulk orders against a business's catalogue; the
business owner approves, processes, and completes them.

| Layer      | Artifact                                                |
|------------|--------------------------------------------------------|
| Entities   | `Order`, `OrderItem`                                    |
| Enum       | `OrderStatus ∈ {PENDING_APPROVAL, APPROVED, REJECTED, MODIFIED, PROCESSING, COMPLETED, CANCELLED}` |
| Service    | `OrderService` (status transitions, discount calc)      |
| Controller | `OrderController`                                       |
| Frontend   | `OrderList.jsx`, `CreateOrder.jsx`, `OrderDetails.jsx`  |

**Key endpoints**
- `POST /api/orders`               — place order (creates `Order` + children `OrderItem`)
- `GET  /api/orders`               — list (filterable by `businessId`, `customerId`)
- `PUT  /api/orders/{id}/approve`
- `PUT  /api/orders/{id}/reject`
- `PUT  /api/orders/{id}/status`   — move through PROCESSING → COMPLETED

Each `OrderItem` stores `quantity`, `unit_price`, `discount_percent`,
`line_total` so the report module can aggregate historical pricing accurately.

---

### 3.4 Function 4 — Inventory & Stock Alerts

**Purpose:** A business manages its own stock levels, receives low-stock
alerts, and reviews a full movement history.

| Layer      | Artifact                                            |
|------------|----------------------------------------------------|
| Entities   | `Product` (stock, moq, lowStockThreshold), `StockMovement`, `StockAlert` |
| Repos      | `StockMovementRepository`, `StockAlertRepository`   |
| Controller | `InventoryController`                               |
| Frontend   | `InventoryPages.jsx` → `InventoryPage`, `StockAlertsPage` |

**Key endpoints (all accept `?businessId=X` for tenant isolation)**
- `GET /api/inventory`                — current stock per product
- `GET /api/inventory/low-stock`      — products below threshold
- `GET /api/inventory/alert-history`  — historical alerts
- `PUT /api/inventory/{id}/stock`     — add/remove stock (audited to `stock_movements`)
- `PUT /api/inventory/{id}/threshold` — update low-stock trigger

**Alert logic:** `checkAndLogAlert()` in `InventoryController` auto-creates a
`StockAlert` row when `stock ≤ threshold`, and marks it resolved when stock
is replenished.

---

### 3.5 Function 5 — Invoice & Payment

**Purpose:** Completed orders generate invoices; customers pay and the system
tracks due dates, overdue aging, and audit trail.

| Layer      | Artifact                                                  |
|------------|----------------------------------------------------------|
| Entities   | `Invoice`, `Payment`, `AuditTrail`                        |
| Enums      | `InvoiceStatus`, `PaymentStatus`, `PaymentMethod`         |
| Service    | `InvoiceService`, `PaymentService`                        |
| Controllers| `InvoiceController`, `PaymentController`                  |
| Frontend   | `InvoicesPage.jsx`, `FinancePages.jsx`, `OrderPayment.jsx`|

**Key endpoints**
- `GET  /api/invoices`                 — list (filterable)
- `GET  /api/invoices/{id}`            — detail
- `POST /api/payments`                 — record payment
- `GET  /api/finance/overdue`          — invoices past due date
- `GET  /api/finance/audit`            — immutable log of money-movements

Every `Payment` insert writes an `AuditTrail` row, giving the admin a
tamper-evident ledger for compliance.

---

### 3.6 Function 6 — Sales Report (Analytics) ⭐ *new*

**Purpose:** Admin runs a single query to see marketplace-wide revenue,
top-selling products, category split, outstanding orders, and inventory
health — all for any date range.

| Layer      | Artifact                                                  |
|------------|----------------------------------------------------------|
| Entity     | `SalesReportSnapshot` (persisted audit of each run)       |
| Repository | `SalesReportSnapshotRepository`                           |
| Controller | `SalesReportController`                                   |
| Frontend   | `SalesReport1.jsx` (admin-only, guarded route)            |

**Key endpoints**
- `GET /api/reports/sales?startDate=&endDate=&persist=&generatedBy=`
  - returns `{ stats, timeSeries, categories, topProducts, outstanding, inventory }`
- `GET /api/reports/sales/history` — last 20 persisted runs

**MySQL table (auto-created by Hibernate)**
```sql
CREATE TABLE sales_report_snapshots (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    start_date        DATE NOT NULL,
    end_date          DATE NOT NULL,
    total_revenue     DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    total_orders      INT NOT NULL DEFAULT 0,
    avg_order_value   DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    active_customers  INT NOT NULL DEFAULT 0,
    generated_by      VARCHAR(255),
    generated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_srs_generated_at (generated_at),
    KEY idx_srs_range (start_date, end_date)
) ENGINE=InnoDB;
```

**Aggregation logic (server-side)**
1. Pull `orders` filtered by `order_date ∈ [start, end]`.
2. Sum `final_amount` → *total revenue*; count rows → *orders*;
   distinct `customer_id` → *active customers*.
3. Group by date → daily `timeSeries` (sales + order count).
4. Walk `order_items`; group by `product.category` → `categories` breakdown.
5. Group by `product_id`, sort by revenue, take top 5 → `topProducts`.
6. Filter `orders` where status ∈ {PENDING_APPROVAL, PROCESSING} → `outstanding`.
7. Scan active `products`, classify as Healthy / Low Stock / Critical → `inventory` health.
8. If `persist=true`, write a row to `sales_report_snapshots` for audit.

**Live verification** (executed 2026-04-22 12:10):
- Total revenue **Rs 400,000** across 2 orders
- Top product: **shirt** (20 units)
- Category: **Textiles**
- 1 snapshot row persisted (id=1)

---

## 4. Database Schema (15 tables)

| # | Table                     | Owner function        |
|---|---------------------------|-----------------------|
| 1 | `admins`                  | Fn 1                  |
| 2 | `business`                | Fn 1                  |
| 3 | `users`                   | Fn 1                  |
| 4 | `customers`               | Fn 3                  |
| 5 | `categories`              | Fn 2                  |
| 6 | `products`                | Fn 2                  |
| 7 | `bulk_pricing`            | Fn 2                  |
| 8 | `orders`                  | Fn 3                  |
| 9 | `order_items`             | Fn 3                  |
| 10| `invoices`                | Fn 5                  |
| 11| `payments`                | Fn 5                  |
| 12| `audit_trail`             | Fn 5                  |
| 13| `reviews`                 | supporting            |
| 14| `rfqs`                    | supporting            |
| 15| `sales_report_snapshots`  | Fn 6 *(new)*          |

Stock tables (`stock_movements`, `stock_alerts`) belong to Fn 4.

---

## 5. Role Matrix

| Feature            | ADMIN | BUSINESS | CUSTOMER |
|--------------------|:-----:|:--------:|:--------:|
| Approvals page     |  ✅   |    —     |    —     |
| Sales Report       |  ✅   |    —     |    —     |
| Product management |   —   |   ✅     |    —     |
| Inventory / Alerts |   —   |   ✅     |    —     |
| Browse marketplace |  ✅   |   ✅     |   ✅     |
| Place order        |   —   |   ✅     |   ✅     |
| Invoices (own)     |   —   |   ✅     |   ✅     |

Guards are enforced **both** client-side (`<Navigate to="..." replace />`)
and server-side (`businessId` filter on every multi-tenant query).

---

## 6. Screens & Navigation

```
Admin
 ├─ /dashboard            (overview, pending-approval count)
 ├─ /admin/approvals      (Fn 1)
 └─ /reports/sales        (Fn 6)

Business
 ├─ /dashboard            (my stats)
 ├─ /products             (Fn 2)
 ├─ /orders               (Fn 3)
 ├─ /inventory            (Fn 4)
 ├─ /inventory/alerts     (Fn 4)
 └─ /invoices             (Fn 5)

Public / Customer
 ├─ /marketplace          (Fn 2 — browse)
 ├─ /product/:id
 ├─ /create-order         (Fn 3)
 └─ /orders/:id/pay       (Fn 5)
```

---

## 7. Testing Summary

| Test                                | Result |
|-------------------------------------|:------:|
| Business registers → PENDING row    |   ✅   |
| Admin approves → status=APPROVED    |   ✅   |
| Business adds product (Test Company)|   ✅   |
| Other company's inventory empty     |   ✅   |
| Customer places order → items saved |   ✅   |
| Admin opens `/reports/sales`        |   ✅   |
| `persist=true` inserts snapshot     |   ✅   |
| `/api/reports/sales/history` returns it | ✅ |
| Business hits `/reports/sales` → redirect `/marketplace` | ✅ |
| Admin hits `/inventory` → redirect `/dashboard` | ✅ |

---

## 8. Conclusion

The system delivers the **six required functions** end-to-end: a business can
register, be approved, list products, hold stock, receive orders, generate
invoices, and have all of that rolled up into an admin-facing sales report
with historical snapshots. Multi-tenancy and role-based access are enforced
at both UI and API layers. The final module added — **Sales Report** — has
its own dedicated controller, DTO-shaped response, a new MySQL table, and
has been verified against live data (Rs 400,000 aggregated across 2 orders).
