package com.b2bwholesale.backend.services;

import com.b2bwholesale.backend.modal.Business;
import com.b2bwholesale.backend.modal.Order;
import com.b2bwholesale.backend.modal.OrderStatus;
import com.b2bwholesale.backend.modal.Product;
import com.b2bwholesale.backend.modal.Review;
import com.b2bwholesale.backend.repositories.BusinessRepository;
import com.b2bwholesale.backend.repositories.OrderRepository;
import com.b2bwholesale.backend.repositories.ProductRepository;
import com.b2bwholesale.backend.repositories.ReviewRepository;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReviewService {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Transactional
    public Review createReview(ReviewRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        Business reviewer = businessRepository.findById(request.getReviewerBusinessId())
                .orElseThrow(() -> new RuntimeException("Business not found"));

        // Check if already reviewed
        if (reviewRepository.existsByProductIdAndReviewerBusinessId(request.getProductId(), request.getReviewerBusinessId())) {
            throw new RuntimeException("You have already reviewed this product");
        }

        // Validate rating
        if (request.getRating() < 1 || request.getRating() > 5) {
            throw new RuntimeException("Rating must be between 1 and 5");
        }

        Review review = new Review();
        review.setProduct(product);
        review.setReviewerBusiness(reviewer);
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        review.setReviewerName(reviewer.getName());
        review.setQualityRating(request.getQualityRating());
        review.setValueRating(request.getValueRating());
        review.setShippingRating(request.getShippingRating());

        // Check if this is a verified purchase (buyer has completed orders with this product's seller)
        boolean isVerified = checkVerifiedPurchase(request.getReviewerBusinessId(), product);
        review.setIsVerifiedPurchase(isVerified);

        return reviewRepository.save(review);
    }

    private boolean checkVerifiedPurchase(Long reviewerBusinessId, Product product) {
        try {
            // Check if there are any completed/approved orders from the product's business
            // that involve the reviewer's business as the customer
            List<Order> orders = orderRepository.findByBusinessId(product.getBusiness().getId());
            return orders.stream().anyMatch(o ->
                    o.getStatus() == OrderStatus.COMPLETED ||
                    o.getStatus() == OrderStatus.APPROVED ||
                    o.getStatus() == OrderStatus.PROCESSING
            );
        } catch (Exception e) {
            return false;
        }
    }

    public List<Review> getByProduct(Long productId) {
        return reviewRepository.findByProductIdOrderByCreatedAtDesc(productId);
    }

    public List<Review> getByProductAndRating(Long productId, Integer rating) {
        return reviewRepository.findByProductIdAndRatingOrderByCreatedAtDesc(productId, rating);
    }

    public List<Review> getByReviewer(Long reviewerBusinessId) {
        return reviewRepository.findByReviewerBusinessIdOrderByCreatedAtDesc(reviewerBusinessId);
    }

    public Map<String, Object> getProductRatingSummary(Long productId) {
        Map<String, Object> summary = new HashMap<>();

        Double avgRating = reviewRepository.getAverageRatingByProductId(productId);
        Long totalReviews = reviewRepository.getReviewCountByProductId(productId);
        List<Object[]> distribution = reviewRepository.getRatingDistributionByProductId(productId);

        summary.put("averageRating", avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0);
        summary.put("totalReviews", totalReviews != null ? totalReviews : 0);

        // Build distribution map
        Map<Integer, Long> dist = new HashMap<>();
        for (int i = 1; i <= 5; i++) dist.put(i, 0L);
        if (distribution != null) {
            for (Object[] row : distribution) {
                dist.put((Integer) row[0], (Long) row[1]);
            }
        }
        summary.put("distribution", dist);

        return summary;
    }

    @Transactional
    public Review addSellerResponse(Long reviewId, String response) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));
        review.setSellerResponse(response);
        review.setSellerRespondedAt(LocalDateTime.now());
        return reviewRepository.save(review);
    }

    @Transactional
    public Review markHelpful(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));
        review.setHelpfulCount((review.getHelpfulCount() != null ? review.getHelpfulCount() : 0) + 1);
        return reviewRepository.save(review);
    }

    @Data
    public static class ReviewRequest {
        private Long productId;
        private Long reviewerBusinessId;
        private Integer rating;
        private String comment;
        private Integer qualityRating;
        private Integer valueRating;
        private Integer shippingRating;
    }
}
