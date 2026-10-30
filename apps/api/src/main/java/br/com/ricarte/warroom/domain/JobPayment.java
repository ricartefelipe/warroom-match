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

/** The money of one job: what the provider confirmed, with the references needed to reconcile. */
@Entity
@Table(name = "job_payments")
public class JobPayment {

    @Id
    private UUID id;

    @Column(name = "job_id", nullable = false, unique = true)
    private UUID jobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentProvider provider;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "platform_fee_cents", nullable = false)
    private long platformFeeCents;

    @Column(name = "pro_amount_cents", nullable = false)
    private long proAmountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "checkout_attempts", nullable = false)
    private int checkoutAttempts;

    @Column(name = "stripe_session_id", length = 200)
    private String stripeSessionId;

    @Column(name = "stripe_payment_intent_id", length = 200)
    private String stripePaymentIntentId;

    @Column(name = "stripe_transfer_id", length = 200)
    private String stripeTransferId;

    @Column(name = "stripe_refund_id", length = 200)
    private String stripeRefundId;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "payout_requested_at")
    private Instant payoutRequestedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "disputed_at")
    private Instant disputedAt;

    @Column(name = "last_reconciled_at")
    private Instant lastReconciledAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected JobPayment() {
    }

    public JobPayment(UUID id, UUID jobId, PaymentProvider provider, String currency, FeeSplit fees, Instant createdAt) {
        this.id = id;
        this.jobId = jobId;
        this.provider = provider;
        this.currency = currency;
        this.amountCents = fees.amountCents();
        this.platformFeeCents = fees.platformFeeCents();
        this.proAmountCents = fees.proAmountCents();
        this.status = PaymentStatus.checkout_created;
        this.checkoutAttempts = 1;
        this.createdAt = createdAt;
    }

    public void transitionTo(PaymentStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalTransitionException("payment", status.name(), target.name());
        }
        this.status = target;
        switch (target) {
            case paid -> this.paidAt = now;
            case payout_requested -> this.payoutRequestedAt = now;
            case released -> this.releasedAt = now;
            case refunded -> this.refundedAt = now;
            case disputed -> this.disputedAt = now;
            case checkout_created -> this.checkoutAttempts++;
            case refund_requested, expired, failed -> {
            }
        }
    }

    public boolean matchesSession(String sessionId) {
        return stripeSessionId != null && stripeSessionId.equals(sessionId);
    }

    public boolean matchesAmount(Long amountTotal, String currency) {
        return amountTotal != null && amountTotal == this.amountCents
                && currency != null && currency.equalsIgnoreCase(this.currency);
    }

    public UUID getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public String getCurrency() {
        return currency;
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

    public PaymentStatus getStatus() {
        return status;
    }

    public int getCheckoutAttempts() {
        return checkoutAttempts;
    }

    public String getStripeSessionId() {
        return stripeSessionId;
    }

    public void setStripeSessionId(String stripeSessionId) {
        this.stripeSessionId = stripeSessionId;
    }

    public String getStripePaymentIntentId() {
        return stripePaymentIntentId;
    }

    public void setStripePaymentIntentId(String stripePaymentIntentId) {
        this.stripePaymentIntentId = stripePaymentIntentId;
    }

    public String getStripeTransferId() {
        return stripeTransferId;
    }

    public void setStripeTransferId(String stripeTransferId) {
        this.stripeTransferId = stripeTransferId;
    }

    public String getStripeRefundId() {
        return stripeRefundId;
    }

    public void setStripeRefundId(String stripeRefundId) {
        this.stripeRefundId = stripeRefundId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getPayoutRequestedAt() {
        return payoutRequestedAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public Instant getRefundedAt() {
        return refundedAt;
    }

    public Instant getDisputedAt() {
        return disputedAt;
    }

    public Instant getLastReconciledAt() {
        return lastReconciledAt;
    }

    public void setLastReconciledAt(Instant lastReconciledAt) {
        this.lastReconciledAt = lastReconciledAt;
    }

    public long getVersion() {
        return version;
    }
}
