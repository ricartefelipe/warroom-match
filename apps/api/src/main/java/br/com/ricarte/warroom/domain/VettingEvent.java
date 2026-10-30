package br.com.ricarte.warroom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Audit trail of every vetting decision: who, when, from what to what, and why. */
@Entity
@Table(name = "vetting_events")
public class VettingEvent {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 20)
    private VettingStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private VettingStatus toStatus;

    @Column(name = "actor_account_id", nullable = false)
    private UUID actorAccountId;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected VettingEvent() {
    }

    public VettingEvent(UUID id, UUID accountId, VettingStatus fromStatus, VettingStatus toStatus,
                        UUID actorAccountId, String note, Instant createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorAccountId = actorAccountId;
        this.note = note;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public VettingStatus getFromStatus() {
        return fromStatus;
    }

    public VettingStatus getToStatus() {
        return toStatus;
    }

    public UUID getActorAccountId() {
        return actorAccountId;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
