package com.syndicate.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    List<Review> findByTransactionIdOrderByReviewedAtDesc(UUID transactionId);

    List<Review> findByTargetTypeAndTargetLineageIdOrderByReviewedAtDesc(ReviewTargetType type, UUID lineageId);

    List<Review> findByTargetTypeAndTargetVersionId(ReviewTargetType type, UUID versionId);

    @Query(value = "SELECT required_role FROM review_requirements WHERE target_type = :type AND materiality = :materiality",
            nativeQuery = true)
    List<String> requiredRoles(@Param("type") String type, @Param("materiality") String materiality);
}
