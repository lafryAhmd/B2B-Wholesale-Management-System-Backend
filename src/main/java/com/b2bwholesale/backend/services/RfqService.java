package com.b2bwholesale.backend.services;

import com.b2bwholesale.backend.modal.*;
import com.b2bwholesale.backend.repositories.BusinessRepository;
import com.b2bwholesale.backend.repositories.ProductRepository;
import com.b2bwholesale.backend.repositories.RfqRepository;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class RfqService {

    @Autowired
    private RfqRepository rfqRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private OrderService orderService;

    private String generateRfqNumber() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long random = (long) (Math.random() * 900000) + 100000;
        return "RFQ-" + datePart + "-" + random;
    }

    @Transactional
    public Rfq createRfq(RfqRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        Business buyerBusiness = businessRepository.findById(request.getBuyerBusinessId())
                .orElseThrow(() -> new RuntimeException("Buyer business not found"));

        Business sellerBusiness = product.getBusiness();
        if (sellerBusiness == null) {
            throw new RuntimeException("Product has no associated seller business");
        }

        Rfq rfq = new Rfq();
        rfq.setRfqNumber(generateRfqNumber());
        rfq.setProduct(product);
        rfq.setBuyerBusiness(buyerBusiness);
        rfq.setSellerBusiness(sellerBusiness);
        rfq.setRequestedQuantity(request.getRequestedQuantity());
        rfq.setMessage(request.getMessage());
        rfq.setStatus("PENDING");

        return rfqRepository.save(rfq);
    }

    public List<Rfq> getAll() {
        return rfqRepository.findAll();
    }

    public List<Rfq> getByBuyer(Long buyerBusinessId) {
        return rfqRepository.findByBuyerBusinessIdOrderByCreatedAtDesc(buyerBusinessId);
    }

    public List<Rfq> getBySeller(Long sellerBusinessId) {
        return rfqRepository.findBySellerBusinessIdOrderByCreatedAtDesc(sellerBusinessId);
    }

    public Rfq getById(Long id) {
        return rfqRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("RFQ not found"));
    }

    public List<Rfq> getPendingBySeller(Long sellerBusinessId) {
        return rfqRepository.findBySellerBusinessIdAndStatus(sellerBusinessId, "PENDING");
    }

    @Transactional
    public Rfq respondToRfq(Long rfqId, RfqResponseRequest request) {
        Rfq rfq = rfqRepository.findById(rfqId)
                .orElseThrow(() -> new RuntimeException("RFQ not found"));

        if (!"PENDING".equals(rfq.getStatus())) {
            throw new RuntimeException("Can only respond to PENDING RFQs");
        }

        rfq.setOfferedPrice(request.getOfferedPrice());
        rfq.setOfferedDiscount(request.getOfferedDiscount());

        // Calculate offered total: (offeredPrice * quantity) - discount amount
        BigDecimal lineTotal = request.getOfferedPrice()
                .multiply(BigDecimal.valueOf(rfq.getRequestedQuantity()));
        if (request.getOfferedDiscount() != null && request.getOfferedDiscount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal discountAmount = lineTotal.multiply(request.getOfferedDiscount())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            lineTotal = lineTotal.subtract(discountAmount);
        }
        rfq.setOfferedTotal(lineTotal);

        rfq.setSellerNotes(request.getSellerNotes());

        int validDays = request.getValidDays() != null ? request.getValidDays() : 7;
        rfq.setValidUntil(LocalDate.now().plusDays(validDays));
        rfq.setRespondedAt(LocalDateTime.now());
        rfq.setStatus("QUOTED");

        return rfqRepository.save(rfq);
    }

    @Transactional
    public Rfq acceptQuote(Long rfqId) {
        Rfq rfq = rfqRepository.findById(rfqId)
                .orElseThrow(() -> new RuntimeException("RFQ not found"));

        if (!"QUOTED".equals(rfq.getStatus())) {
            throw new RuntimeException("Can only accept QUOTED RFQs");
        }

        // Check if quote has expired
        if (rfq.getValidUntil() != null && rfq.getValidUntil().isBefore(LocalDate.now())) {
            rfq.setStatus("EXPIRED");
            rfqRepository.save(rfq);
            throw new RuntimeException("This quote has expired");
        }

        rfq.setStatus("ACCEPTED");

        // Create order from the RFQ using the offered price
        OrderItem orderItem = new OrderItem();
        Product product = rfq.getProduct();
        orderItem.setProduct(product);
        orderItem.setQuantity(rfq.getRequestedQuantity());

        List<OrderItem> items = new ArrayList<>();
        items.add(orderItem);

        String notes = "Order from RFQ #" + rfq.getRfqNumber();

        // Skip stock check — seller already committed supply via accepted quote (treated as backorder if short)
        Order order = orderService.createOrder(
                null,
                rfq.getSellerBusiness().getId(),
                rfq.getBuyerBusiness().getId(),
                notes,
                items,
                true
        );

        rfq.setOrderId(order.getId());
        rfq.setStatus("ORDERED");

        return rfqRepository.save(rfq);
    }

    @Transactional
    public Rfq rejectQuote(Long rfqId) {
        Rfq rfq = rfqRepository.findById(rfqId)
                .orElseThrow(() -> new RuntimeException("RFQ not found"));

        if (!"QUOTED".equals(rfq.getStatus())) {
            throw new RuntimeException("Can only reject QUOTED RFQs");
        }

        rfq.setStatus("REJECTED");
        return rfqRepository.save(rfq);
    }

    @Transactional
    public void expireQuotes() {
        List<Rfq> quotedRfqs = rfqRepository.findByStatus("QUOTED");
        for (Rfq rfq : quotedRfqs) {
            if (rfq.getValidUntil() != null && rfq.getValidUntil().isBefore(LocalDate.now())) {
                rfq.setStatus("EXPIRED");
                rfqRepository.save(rfq);
            }
        }
    }

    @Data
    public static class RfqRequest {
        private Long productId;
        private Long buyerBusinessId;
        private Integer requestedQuantity;
        private String message;
    }

    @Data
    public static class RfqResponseRequest {
        private BigDecimal offeredPrice;
        private BigDecimal offeredDiscount;
        private String sellerNotes;
        private Integer validDays = 7;
    }
}
