package com.b2bwholesale.backend.controller;

import com.b2bwholesale.backend.modal.Order;
import com.b2bwholesale.backend.modal.OrderItem;
import com.b2bwholesale.backend.modal.OrderStatus;
import com.b2bwholesale.backend.modal.Product;
import com.b2bwholesale.backend.repositories.OrderRepository;
import com.b2bwholesale.backend.services.OrderService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody OrderRequest request) {
        try {
            List<OrderItem> items = request.getItems().stream().map(dto -> {
                OrderItem item = new OrderItem();
                Product p = new Product();
                p.setId(dto.getProductId());
                item.setProduct(p);
                item.setQuantity(dto.getQuantity());
                return item;
            }).collect(Collectors.toList());

            Order order = orderService.createOrder(
                    request.getCustomerId(),
                    request.getBusinessId(),
                    request.getBuyerBusinessId(),
                    request.getNotes(),
                    items
            );
            return ResponseEntity.ok(order);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> modifyOrder(@PathVariable Long id, @RequestBody OrderRequest request) {
        try {
            List<OrderItem> items = request.getItems().stream().map(dto -> {
                OrderItem item = new OrderItem();
                Product p = new Product();
                p.setId(dto.getProductId());
                item.setProduct(p);
                item.setQuantity(dto.getQuantity());
                return item;
            }).collect(Collectors.toList());

            Order updated = orderService.modifyOrder(id, request.getNotes(), items);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<?> adminApproveOrder(@PathVariable Long id) {
        try {
            Order approved = orderService.adminApproveOrder(id);
            return ResponseEntity.ok(approved);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<?> rejectOrder(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        try {
            String reason = body != null ? body.get("reason") : null;
            Order rejected = orderService.rejectOrder(id, reason);
            return ResponseEntity.ok(rejected);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    @GetMapping("/pending")
    public List<Order> getPendingOrders() {
        return orderRepository.findByStatus(OrderStatus.PENDING_APPROVAL);
    }

    @GetMapping("/approved")
    public List<Order> getApprovedOrders() {
        return orderRepository.findByStatus(OrderStatus.APPROVED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable Long id) {
        return orderRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/customer/{customerId}")
    public List<Order> getOrdersByCustomer(@PathVariable Long customerId) {
        return orderRepository.findByCustomerId(customerId);
    }

    @GetMapping("/business/{businessId}")
    public List<Order> getOrdersByBusiness(@PathVariable Long businessId) {
        return orderRepository.findByBusinessId(businessId);
    }
}

@Data
class OrderRequest {
    private Long customerId;
    private Long businessId;
    private Long buyerBusinessId;
    private String notes;
    private List<OrderItemRequest> items;
}

@Data
class OrderItemRequest {
    private Long productId;
    private Integer quantity;
}
