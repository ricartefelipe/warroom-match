package br.com.ricarte.warroom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A disagreement that blocks money until a human in support decides. */
@Entity
@Table(name = "disputes")
public class Dispute {

    @Id
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "opened_by_account_id", nullable = false)
    private UUID openedByAccountId;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DisputeStatus status;

    @Column(name = "resolved_by_account_id")
    private UUID resolvedByAccountId;

    @Column(name = "resolution_note", columnDefinition = "text")
    private String resolutionNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Dispute() {
    }

    public Dispute(UUID id, UUID jobId, UUID openedByAccountId, String reason, Instant createdAt) {
        this.id = id;
        this.jobId = jobId;
        this.openedByAccountId = openedByAccountId;
        this.reason = reason;
        this.status = DisputeStatus.open;
        this.createdAt = createdAt;
    }

    public void resolve(DisputeStatus outcome, UUID actorAccountId, String note, Instant now) {
        if (status != DisputeStatus.open) {
            throw new IllegalTransitionException("dispute", status.name(), outcome.name());
        }
        if (outcome == DisputeStatus.open) {
            throw new IllegalArgumentException("a dispute is resolved with release or refund");
        }
        this.status = outcome;
        this.resolvedByAccountId = actorAccountId;
        this.resolutionNote = note;
        this.resolvedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public UUID getOpenedByAccountId() {
        return openedByAccountId;
    }

    public String getReason() {
        return reason;
    }

    public DisputeStatus getStatus() {
        return status;
    }

    public UUID getResolvedByAccountId() {
        return resolvedByAccountId;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
