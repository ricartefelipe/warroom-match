package br.com.ricarte.warroom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reviews")
public class Review {

    @Id
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "reviewer_account_id", nullable = false)
    private UUID reviewerAccountId;

    @Column(name = "reviewee_account_id", nullable = false)
    private UUID revieweeAccountId;

    @Column(nullable = false)
    private int rating;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Review() {
    }

    public Review(
            UUID id,
            UUID jobId,
            UUID reviewerAccountId,
            UUID revieweeAccountId,
            int rating,
            String comment,
            Instant createdAt
    ) {
        this.id = id;
        this.jobId = jobId;
        this.reviewerAccountId = reviewerAccountId;
        this.revieweeAccountId = revieweeAccountId;
        this.rating = rating;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public UUID getReviewerAccountId() {
        return reviewerAccountId;
    }

    public UUID getRevieweeAccountId() {
        return revieweeAccountId;
    }

    public int getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
