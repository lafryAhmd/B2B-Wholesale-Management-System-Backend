package com.b2bwholesale.backend.controller;

import com.b2bwholesale.backend.modal.Review;
import com.b2bwholesale.backend.services.ReviewService;
import com.b2bwholesale.backend.services.ReviewService.ReviewRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    @PostMapping
    public ResponseEntity<?> createReview(@RequestBody ReviewRequest request) {
        try {
            Review review = reviewService.createReview(request);
            return ResponseEntity.ok(review);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/product/{productId}")
    public List<Review> getByProduct(@PathVariable Long productId) {
        return reviewService.getByProduct(productId);
    }

    @GetMapping("/product/{productId}/filter")
    public List<Review> getByProductAndRating(@PathVariable Long productId, @RequestParam Integer rating) {
        return reviewService.getByProductAndRating(productId, rating);
    }

    @GetMapping("/product/{productId}/summary")
    public Map<String, Object> getRatingSummary(@PathVariable Long productId) {
        return reviewService.getProductRatingSummary(productId);
    }

    @GetMapping("/business/{businessId}")
    public List<Review> getByReviewer(@PathVariable Long businessId) {
        return reviewService.getByReviewer(businessId);
    }

    @PutMapping("/{reviewId}/respond")
    public ResponseEntity<?> addSellerResponse(@PathVariable Long reviewId, @RequestBody Map<String, String> body) {
        try {
            Review review = reviewService.addSellerResponse(reviewId, body.get("response"));
            return ResponseEntity.ok(review);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/{reviewId}/helpful")
    public ResponseEntity<?> markHelpful(@PathVariable Long reviewId) {
        try {
            Review review = reviewService.markHelpful(reviewId);
            return ResponseEntity.ok(review);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
