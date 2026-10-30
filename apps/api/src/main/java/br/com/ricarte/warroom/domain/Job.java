package br.com.ricarte.warroom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "jobs")
public class Job {

    @Id
    private UUID id;

    @Column(name = "company_account_id", nullable = false)
    private UUID companyAccountId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 10)
    private String urgency;

    @Column(name = "budget_cents", nullable = false)
    private long budgetCents;

    @Column(name = "skills_needed", columnDefinition = "text")
    private String skillsNeeded;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private JobStatus status;

    @Column(name = "matched_pro_account_id")
    private UUID matchedProAccountId;

    @Column(name = "response_deadline_at")
    private Instant responseDeadlineAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic lock: two concurrent transitions of the same job cannot both win. */
    @Version
    @Column(nullable = false)
    private long version;

    protected Job() {
    }

    public Job(
            UUID id,
            UUID companyAccountId,
            String title,
            String description,
            String urgency,
            long budgetCents,
            String skillsNeeded,
            JobStatus status,
            Instant responseDeadlineAt,
            Instant createdAt
    ) {
        this.id = id;
        this.companyAccountId = companyAccountId;
        this.title = title;
        this.description = description;
        this.urgency = urgency;
        this.budgetCents = budgetCents;
        this.skillsNeeded = skillsNeeded;
        this.status = status;
        this.responseDeadlineAt = responseDeadlineAt;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /** Moves to {@code target} or throws {@link IllegalTransitionException}. */
    public void transitionTo(JobStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalTransitionException("job", status.name(), target.name());
        }
        this.status = target;
        this.updatedAt = now;
        if (target == JobStatus.delivered) {
            this.deliveredAt = now;
        }
    }

    public void match(UUID proAccountId, Instant now) {
        transitionTo(JobStatus.matched, now);
        this.matchedProAccountId = proAccountId;
    }

    public boolean deadlinePassed(Instant now) {
        return responseDeadlineAt != null && now.isAfter(responseDeadlineAt);
    }

    public boolean isOwner(UUID accountId) {
        return companyAccountId.equals(accountId);
    }

    public boolean isMatchedPro(UUID accountId) {
        return matchedProAccountId != null && matchedProAccountId.equals(accountId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyAccountId() {
        return companyAccountId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getUrgency() {
        return urgency;
    }

    public long getBudgetCents() {
        return budgetCents;
    }

    public String getSkillsNeeded() {
        return skillsNeeded;
    }

    public JobStatus getStatus() {
        return status;
    }

    public UUID getMatchedProAccountId() {
        return matchedProAccountId;
    }

    public Instant getResponseDeadlineAt() {
        return responseDeadlineAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
