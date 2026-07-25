package br.com.ricarte.warroom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
            Instant createdAt,
            Instant updatedAt
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
        this.updatedAt = updatedAt;
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

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public UUID getMatchedProAccountId() {
        return matchedProAccountId;
    }

    public void setMatchedProAccountId(UUID matchedProAccountId) {
        this.matchedProAccountId = matchedProAccountId;
    }

    public Instant getResponseDeadlineAt() {
        return responseDeadlineAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
