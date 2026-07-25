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
@Table(name = "applications")
public class Application {

    @Id
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "pro_account_id", nullable = false)
    private UUID proAccountId;

    @Column(nullable = false, columnDefinition = "text")
    private String pitch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Application() {
    }

    public Application(UUID id, UUID jobId, UUID proAccountId, String pitch, ApplicationStatus status, Instant createdAt) {
        this.id = id;
        this.jobId = jobId;
        this.proAccountId = proAccountId;
        this.pitch = pitch;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public UUID getProAccountId() {
        return proAccountId;
    }

    public String getPitch() {
        return pitch;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
