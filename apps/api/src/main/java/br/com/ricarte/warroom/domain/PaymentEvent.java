package br.com.ricarte.warroom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable record of what changed a payment and why: provider webhooks (deduplicated by the
 * provider's event id), platform actions and reconciliations.
 */
@Entity
@Table(name = "payment_events")
public class PaymentEvent {

    @Id
    private UUID id;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(nullable = false, length = 80)
    private String type;

    @Column(name = "provider_event_id", length = 120, unique = true)
    private String providerEventId;

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", length = 30)
    private String toStatus;

    @Column(columnDefinition = "text")
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PaymentEvent() {
    }

    public PaymentEvent(UUID id, UUID paymentId, String type, String providerEventId,
                        String fromStatus, String toStatus, String detail, Instant createdAt) {
        this.id = id;
        this.paymentId = paymentId;
        this.type = type;
        this.providerEventId = providerEventId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getType() {
        return type;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
