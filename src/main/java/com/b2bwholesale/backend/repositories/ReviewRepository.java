package com.b2bwholesale.backend.repositories;

import com.b2bwholesale.backend.modal.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductIdOrderByCreatedAtDesc(Long productId);

    List<Review> findByReviewerBusinessIdOrderByCreatedAtDesc(Long reviewerBusinessId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.productId = :productId")
    Double getAverageRatingByProductId(Long productId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.productId = :productId")
    Long getReviewCountByProductId(Long productId);

    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.productId = :productId GROUP BY r.rating ORDER BY r.rating DESC")
    List<Object[]> getRatingDistributionByProductId(Long productId);

    boolean existsByProductIdAndReviewerBusinessId(Long productId, Long reviewerBusinessId);

    List<Review> findByProductIdAndRatingOrderByCreatedAtDesc(Long productId, Integer rating);
}
