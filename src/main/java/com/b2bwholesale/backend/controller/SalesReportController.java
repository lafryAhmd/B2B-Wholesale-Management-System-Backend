package com.b2bwholesale.backend.controller;

import com.b2bwholesale.backend.modal.*;
import com.b2bwholesale.backend.repositories.OrderRepository;
import com.b2bwholesale.backend.repositories.ProductRepository;
import com.b2bwholesale.backend.repositories.SalesReportSnapshotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Server-side aggregation for the Sales Report page (admin-only screen).
 * One call returns every block the UI renders: headline stats, daily time-series,
 * category breakdown, top products, outstanding invoices and inventory health.
 */
@RestController
@RequestMapping("/api/reports/sales")
public class SalesReportController {

    @Autowired private OrderRepository orderRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private SalesReportSnapshotRepository snapshotRepository;

    @GetMapping
    public Map<String, Object> getSalesReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "false") boolean persist,
            @RequestParam(required = false) String generatedBy) {

        LocalDate today = LocalDate.now();
        LocalDate start = startDate != null && !startDate.isBlank() ? LocalDate.parse(startDate) : today.minusDays(30);
        LocalDate end   = endDate   != null && !endDate.isBlank()   ? LocalDate.parse(endDate)   : today;

        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt   = end.atTime(23, 59, 59);

        List<Order> all = orderRepository.findAll();

        // ── Filter orders in range ────────────────────────────────────────────
        List<Order> inRange = all.stream()
                .filter(o -> o.getOrderDate() != null
                        && !o.getOrderDate().isBefore(startDt)
                        && !o.getOrderDate().isAfter(endDt))
                .collect(Collectors.toList());

        // ── Headline stats ────────────────────────────────────────────────────
        BigDecimal totalRevenue = inRange.stream()
                .map(o -> o.getFinalAmount() != null ? o.getFinalAmount() : o.getTotalAmount())
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalOrders = inRange.size();
        BigDecimal avgOrderValue = totalOrders > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Set<Long> customerIds = inRange.stream()
                .map(Order::getCustomerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalRevenue", totalRevenue);
        stats.put("totalOrders", totalOrders);
        stats.put("avgOrderValue", avgOrderValue);
        stats.put("activeCustomers", customerIds.size());

        // ── Time-series (date → sales, orders) ────────────────────────────────
        Map<String, double[]> dailyMap = new TreeMap<>();
        DateTimeFormatter df = DateTimeFormatter.ofPattern("MM-dd");
        for (Order o : inRange) {
            String k = o.getOrderDate().toLocalDate().format(df);
            double amt = (o.getFinalAmount() != null ? o.getFinalAmount() : o.getTotalAmount()).doubleValue();
            dailyMap.computeIfAbsent(k, x -> new double[]{0, 0})[0] += amt;
            dailyMap.get(k)[1] += 1;
        }
        List<Map<String, Object>> timeSeries = new ArrayList<>();
        for (Map.Entry<String, double[]> e : dailyMap.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", e.getKey());
            row.put("sales", e.getValue()[0]);
            row.put("orders", (int) e.getValue()[1]);
            timeSeries.add(row);
        }

        // ── Top products & category breakdown ────────────────────────────────
        Map<Long, int[]> productQty = new HashMap<>();           // productId → qty
        Map<Long, BigDecimal> productRevenue = new HashMap<>();  // productId → revenue
        Map<String, BigDecimal> categoryRevenue = new LinkedHashMap<>();

        for (Order o : inRange) {
            if (o.getItems() == null) continue;
            for (OrderItem it : o.getItems()) {
                if (it.getProduct() == null) continue;
                Long pid = it.getProduct().getId();
                int q = it.getQuantity() == null ? 0 : it.getQuantity();
                BigDecimal line = it.getLineTotal() != null ? it.getLineTotal()
                        : (it.getUnitPrice() != null ? it.getUnitPrice().multiply(BigDecimal.valueOf(q)) : BigDecimal.ZERO);

                productQty.merge(pid, new int[]{q}, (a, b) -> new int[]{a[0] + b[0]});
                productRevenue.merge(pid, line, BigDecimal::add);

                String cat = it.getProduct().getCategory() != null ? it.getProduct().getCategory() : "Uncategorized";
                categoryRevenue.merge(cat, line, BigDecimal::add);
            }
        }

        Map<Long, Product> productIndex = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        List<Map<String, Object>> topProducts = productRevenue.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(5)
                .map(e -> {
                    Product p = productIndex.get(e.getKey());
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("productId", e.getKey());
                    m.put("name", p != null ? p.getName() : "Unknown");
                    m.put("sales", productQty.getOrDefault(e.getKey(), new int[]{0})[0]);
                    m.put("revenue", e.getValue());
                    return m;
                })
                .collect(Collectors.toList());

        String[] palette = {"#0f2044", "#3b82f6", "#0ea5e9", "#93c5fd", "#d97706", "#16a34a"};
        List<Map<String, Object>> categories = new ArrayList<>();
        int idx = 0;
        for (Map.Entry<String, BigDecimal> e : categoryRevenue.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", e.getKey());
            m.put("value", e.getValue());
            m.put("color", palette[idx++ % palette.length]);
            categories.add(m);
        }

        // ── Outstanding orders (pending/unpaid, any date) ────────────────────
        List<Map<String, Object>> outstanding = all.stream()
                .filter(o -> o.getStatus() == OrderStatus.PENDING_APPROVAL
                          || o.getStatus() == OrderStatus.PROCESSING)
                .sorted((a, b) -> b.getOrderDate().compareTo(a.getOrderDate()))
                .limit(10)
                .map(o -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", "ORD-" + o.getId());
                    m.put("customer", o.getCustomer() != null
                            ? (o.getCustomer().getName() != null
                                ? o.getCustomer().getName()
                                : o.getCustomer().getEmail())
                            : "Unknown Buyer");
                    m.put("amount", o.getFinalAmount() != null ? o.getFinalAmount() : o.getTotalAmount());
                    m.put("date", o.getOrderDate().toLocalDate().toString());
                    m.put("status", o.getStatus().name());
                    return m;
                })
                .collect(Collectors.toList());

        // ── Inventory health snapshot ─────────────────────────────────────────
        List<Map<String, Object>> inventory = productRepository.findByIsDeletedFalse().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .limit(7)
                .map(p -> {
                    int stock = p.getStock() == null ? 0 : p.getStock();
                    int moq = p.getMoq() == null ? 0 : p.getMoq();
                    String status = "Healthy";
                    if (stock <= moq) status = "Critical";
                    else if (stock < 50) status = "Low Stock";
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("productId", p.getId());
                    m.put("name", p.getName());
                    m.put("stock", stock);
                    m.put("status", status);
                    return m;
                })
                .collect(Collectors.toList());

        // ── Optionally persist the run as an audit record ────────────────────
        if (persist) {
            SalesReportSnapshot snap = new SalesReportSnapshot();
            snap.setStartDate(start);
            snap.setEndDate(end);
            snap.setTotalRevenue(totalRevenue);
            snap.setTotalOrders(totalOrders);
            snap.setAvgOrderValue(avgOrderValue);
            snap.setActiveCustomers(customerIds.size());
            snap.setGeneratedBy(generatedBy != null ? generatedBy : "Admin");
            snapshotRepository.save(snap);
        }

        // ── Response envelope ────────────────────────────────────────────────
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("startDate", start.toString());
        resp.put("endDate", end.toString());
        resp.put("stats", stats);
        resp.put("timeSeries", timeSeries);
        resp.put("categories", categories);
        resp.put("topProducts", topProducts);
        resp.put("outstanding", outstanding);
        resp.put("inventory", inventory);
        return resp;
    }

    /** Report-generation history — lets admin see past runs. */
    @GetMapping("/history")
    public List<SalesReportSnapshot> history() {
        return snapshotRepository.findTop20ByOrderByGeneratedAtDesc();
    }
}
