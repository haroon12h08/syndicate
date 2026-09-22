package com.syndicate.review;

import com.syndicate.review.dto.CreateReviewRequest;
import com.syndicate.review.dto.MissingReviewDto;
import com.syndicate.review.dto.ReviewDto;
import com.syndicate.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/api/facts/{id}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewDto reviewFact(@PathVariable UUID id, @Valid @RequestBody CreateReviewRequest request,
                                @AuthenticationPrincipal User currentUser) {
        return reviewService.reviewFact(id, request, currentUser);
    }

    /** Every review of any version of this fact, each flagged with whether it still covers the current version. */
    @GetMapping("/api/facts/{id}/reviews")
    public List<ReviewDto> listForFact(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return reviewService.listForFact(id, currentUser.getId());
    }

    @GetMapping("/api/transactions/{id}/reviews/missing")
    public List<MissingReviewDto> missing(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return reviewService.missing(id, currentUser.getId());
    }
}
