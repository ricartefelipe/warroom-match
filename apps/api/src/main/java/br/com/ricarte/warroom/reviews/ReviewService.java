package br.com.ricarte.warroom.reviews;

import br.com.ricarte.warroom.auth.AuthService;
import br.com.ricarte.warroom.domain.Job;
import br.com.ricarte.warroom.domain.JobRepository;
import br.com.ricarte.warroom.domain.JobStatus;
import br.com.ricarte.warroom.domain.Review;
import br.com.ricarte.warroom.domain.ReviewRepository;
import br.com.ricarte.warroom.web.ApiException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {

    private final AuthService authService;
    private final JobRepository jobRepository;
    private final ReviewRepository reviewRepository;

    public ReviewService(
            AuthService authService,
            JobRepository jobRepository,
            ReviewRepository reviewRepository
    ) {
        this.authService = authService;
        this.jobRepository = jobRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional
    public Map<String, Object> createReview(UUID accountId, UUID jobId, int rating, String comment) {
        if (rating < 1 || rating > 5) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_rating");
        }
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "job_not_found"));
        if (job.getStatus() != JobStatus.completed) {
            throw new ApiException(HttpStatus.CONFLICT, "job_not_completed");
        }

        UUID revieweeId = resolveReviewee(accountId, job);
        if (reviewRepository.findByJobIdAndReviewerAccountId(jobId, accountId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "review_already_exists");
        }

        Review review = new Review(
                UUID.randomUUID(),
                jobId,
                accountId,
                revieweeId,
                rating,
                comment,
                Instant.now()
        );
        reviewRepository.save(review);
        return toResponse(review);
    }

    private UUID resolveReviewee(UUID reviewerId, Job job) {
        if (job.getCompanyAccountId().equals(reviewerId)) {
            if (job.getMatchedProAccountId() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "no_matched_pro");
            }
            return job.getMatchedProAccountId();
        }
        if (job.getMatchedProAccountId() != null && job.getMatchedProAccountId().equals(reviewerId)) {
            return job.getCompanyAccountId();
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "job_access_denied");
    }

    private Map<String, Object> toResponse(Review review) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reviewId", review.getId().toString());
        body.put("jobId", review.getJobId().toString());
        body.put("reviewerAccountId", review.getReviewerAccountId().toString());
        body.put("revieweeAccountId", review.getRevieweeAccountId().toString());
        body.put("rating", review.getRating());
        body.put("comment", review.getComment());
        body.put("createdAt", review.getCreatedAt().toString());
        return body;
    }
}
