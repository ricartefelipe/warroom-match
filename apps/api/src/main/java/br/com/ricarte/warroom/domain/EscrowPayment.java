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
@Table(name = "escrow_payments")
public class EscrowPayment {

    @Id
    private UUID id;

    @Column(name = "job_id", nullable = false, unique = true)
    private UUID jobId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "platform_fee_cents", nullable = false)
    private long platformFeeCents;

    @Column(name = "pro_amount_cents", nullable = false)
    private long proAmountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EscrowStatus status;

    @Column(name = "stripe_session_id", length = 200)
    private String stripeSessionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    protected EscrowPayment() {
    }

    public EscrowPayment(
            UUID id,
            UUID jobId,
            long amountCents,
            long platformFeeCents,
            long proAmountCents,
            EscrowStatus status,
            String stripeSessionId,
            Instant createdAt
    ) {
        this.id = id;
        this.jobId = jobId;
        this.amountCents = amountCents;
        this.platformFeeCents = platformFeeCents;
        this.proAmountCents = proAmountCents;
        this.status = status;
        this.stripeSessionId = stripeSessionId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public long getPlatformFeeCents() {
        return platformFeeCents;
    }

    public long getProAmountCents() {
        return proAmountCents;
    }

    public EscrowStatus getStatus() {
        return status;
    }

    public void setStatus(EscrowStatus status) {
        this.status = status;
    }

    public String getStripeSessionId() {
        return stripeSessionId;
    }

    public void setStripeSessionId(String stripeSessionId) {
        this.stripeSessionId = stripeSessionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(Instant releasedAt) {
        this.releasedAt = releasedAt;
    }
}
