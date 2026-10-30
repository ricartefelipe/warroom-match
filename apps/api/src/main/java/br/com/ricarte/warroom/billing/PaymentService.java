package br.com.ricarte.warroom.billing;

import br.com.ricarte.warroom.config.WarroomProperties;
import br.com.ricarte.warroom.domain.FeeSplit;
import br.com.ricarte.warroom.domain.Job;
import br.com.ricarte.warroom.domain.JobPayment;
import br.com.ricarte.warroom.domain.JobPaymentRepository;
import br.com.ricarte.warroom.domain.JobStatus;
import br.com.ricarte.warroom.domain.PaymentEvent;
import br.com.ricarte.warroom.domain.PaymentEventRepository;
import br.com.ricarte.warroom.domain.PaymentProvider;
import br.com.ricarte.warroom.domain.PaymentStatus;
import br.com.ricarte.warroom.domain.Profile;
import br.com.ricarte.warroom.domain.ProfileRepository;
import br.com.ricarte.warroom.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Money of a job. Transitions only follow provider confirmations (or the explicit demo gateway);
 * every change leaves a {@link PaymentEvent}. Callers decide the job transition separately.
 */
@Service
public class PaymentService {

    public static final String EVENT_CHECKOUT_CREATED = "checkout_created";
    public static final String EVENT_PAYOUT_REQUESTED = "payout_requested";
    public static final String EVENT_REFUND_REQUESTED = "refund_requested";
    public static final String EVENT_DISPUTE_OPENED = "dispute_opened";
    public static final String EVENT_RECONCILED = "reconciled";

    private final WarroomProperties properties;
    private final PaymentGateway gateway;
    private final JobPaymentRepository payments;
    private final PaymentEventRepository events;
    private final ProfileRepository profiles;

    public PaymentService(WarroomProperties properties, PaymentGateway gateway, JobPaymentRepository payments,
                          PaymentEventRepository events, ProfileRepository profiles) {
        this.properties = properties;
        this.gateway = gateway;
        this.payments = payments;
        this.events = events;
        this.profiles = profiles;
    }

    public boolean demo() {
        return properties.billing().demo();
    }

    public FeeSplit fees(long amountCents) {
        return FeeSplit.of(amountCents, properties.billing().platformFeeBps());
    }

    /**
     * Creates (or reuses) the checkout of a matched job. A second call while a checkout is open
     * returns the same session; a paid job cannot be charged again.
     */
    @Transactional
    public CheckoutResult checkout(Job job) {
        Instant now = Instant.now();
        JobPayment payment = payments.findByJobId(job.getId()).orElse(null);
        if (payment == null) {
            payment = payments.save(new JobPayment(UUID.randomUUID(), job.getId(), provider(),
                    properties.billing().currency(), fees(job.getBudgetCents()), now));
        } else if (payment.getStatus() == PaymentStatus.checkout_created && payment.getStripeSessionId() != null) {
            return new CheckoutResult(payment, null, false);
        } else if (payment.getStatus().retryableCheckout()) {
            record(payment, EVENT_CHECKOUT_CREATED, null, payment.getStatus(), PaymentStatus.checkout_created, "retry", now);
            payment.transitionTo(PaymentStatus.checkout_created, now);
        } else {
            throw new ApiException(HttpStatus.CONFLICT, "payment_already_exists");
        }
        PaymentGateway.Checkout checkout = gateway.createCheckout(job, payment);
        payment.setStripeSessionId(checkout.sessionId());
        record(payment, EVENT_CHECKOUT_CREATED, null, null, PaymentStatus.checkout_created, checkout.sessionId(), now);
        if (checkout.paidImmediately()) {
            record(payment, "paid", null, PaymentStatus.checkout_created, PaymentStatus.paid, "demo", now);
            payment.transitionTo(PaymentStatus.paid, now);
        }
        return new CheckoutResult(payments.save(payment), checkout.url(), checkout.paidImmediately());
    }

    /** Records a provider confirmation of payment. Returns false when nothing changed. */
    @Transactional
    public boolean confirmPaid(JobPayment payment, String providerEventId, String paymentIntentId, Instant now) {
        if (payment.getStatus() == PaymentStatus.paid) {
            record(payment, "paid", providerEventId, payment.getStatus(), payment.getStatus(), "already paid", now);
            return false;
        }
        if (!payment.getStatus().canTransitionTo(PaymentStatus.paid)) {
            record(payment, "paid_ignored", providerEventId, payment.getStatus(), payment.getStatus(),
                    "late event ignored: payment already " + payment.getStatus(), now);
            return false;
        }
        record(payment, "paid", providerEventId, payment.getStatus(), PaymentStatus.paid, paymentIntentId, now);
        payment.setStripePaymentIntentId(paymentIntentId);
        payment.transitionTo(PaymentStatus.paid, now);
        payments.save(payment);
        return true;
    }

    @Transactional
    public void markCheckoutOutcome(JobPayment payment, PaymentStatus outcome, String providerEventId, String detail, Instant now) {
        if (!payment.getStatus().canTransitionTo(outcome)) {
            record(payment, outcome.name() + "_ignored", providerEventId, payment.getStatus(), payment.getStatus(), detail, now);
            return;
        }
        record(payment, outcome.name(), providerEventId, payment.getStatus(), outcome, detail, now);
        payment.setFailureReason(detail);
        payment.transitionTo(outcome, now);
        payments.save(payment);
    }

    @Transactional
    public void markDisputedByProvider(JobPayment payment, String providerEventId, String detail, Instant now) {
        if (!payment.getStatus().canTransitionTo(PaymentStatus.disputed)) {
            record(payment, "disputed_ignored", providerEventId, payment.getStatus(), payment.getStatus(), detail, now);
            return;
        }
        record(payment, "disputed", providerEventId, payment.getStatus(), PaymentStatus.disputed, detail, now);
        payment.transitionTo(PaymentStatus.disputed, now);
        payments.save(payment);
    }

    /**
     * Asks the provider to pay the professional. Ends in {@code released} only with a confirmed
     * transfer; otherwise stays {@code payout_requested} with the reason.
     */
    @Transactional
    public JobPayment requestPayout(Job job, Instant now) {
        JobPayment payment = requirePayment(job.getId());
        if (payment.getStatus() == PaymentStatus.paid || payment.getStatus() == PaymentStatus.disputed) {
            record(payment, EVENT_PAYOUT_REQUESTED, null, payment.getStatus(), PaymentStatus.payout_requested, null, now);
            payment.transitionTo(PaymentStatus.payout_requested, now);
        } else if (payment.getStatus() != PaymentStatus.payout_requested) {
            throw new ApiException(HttpStatus.CONFLICT, "payment_not_releasable");
        }
        Profile professional = profiles.findById(job.getMatchedProAccountId())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "pro_profile_missing"));
        Optional<PaymentGateway.Confirmation> confirmation = gateway.requestPayout(payment, professional);
        if (confirmation.isPresent()) {
            payment.setStripeTransferId(confirmation.get().remoteId());
            payment.setFailureReason(null);
            record(payment, "released", null, PaymentStatus.payout_requested, PaymentStatus.released,
                    confirmation.get().remoteId(), now);
            payment.transitionTo(PaymentStatus.released, now);
        } else {
            payment.setFailureReason("pro_not_onboarded");
            record(payment, "payout_pending", null, payment.getStatus(), payment.getStatus(), "pro_not_onboarded", now);
        }
        return payments.save(payment);
    }

    @Transactional
    public JobPayment requestRefund(Job job, Instant now) {
        JobPayment payment = requirePayment(job.getId());
        if (payment.getStatus() == PaymentStatus.paid || payment.getStatus() == PaymentStatus.disputed) {
            record(payment, EVENT_REFUND_REQUESTED, null, payment.getStatus(), PaymentStatus.refund_requested, null, now);
            payment.transitionTo(PaymentStatus.refund_requested, now);
        } else if (payment.getStatus() != PaymentStatus.refund_requested) {
            throw new ApiException(HttpStatus.CONFLICT, "payment_not_refundable");
        }
        Optional<PaymentGateway.Confirmation> confirmation = gateway.requestRefund(payment);
        if (confirmation.isPresent()) {
            payment.setStripeRefundId(confirmation.get().remoteId());
            record(payment, "refunded", null, PaymentStatus.refund_requested, PaymentStatus.refunded,
                    confirmation.get().remoteId(), now);
            payment.transitionTo(PaymentStatus.refunded, now);
        } else {
            record(payment, "refund_pending", null, payment.getStatus(), payment.getStatus(), "awaiting provider", now);
        }
        return payments.save(payment);
    }

    @Transactional
    public JobPayment openDispute(Job job, Instant now) {
        JobPayment payment = requirePayment(job.getId());
        if (payment.getStatus().canTransitionTo(PaymentStatus.disputed)) {
            record(payment, EVENT_DISPUTE_OPENED, null, payment.getStatus(), PaymentStatus.disputed, null, now);
            payment.transitionTo(PaymentStatus.disputed, now);
        }
        return payments.save(payment);
    }

    /** Compares the local state with the provider's and moves forward only what the provider confirmed. */
    @Transactional
    public JobPayment reconcile(UUID paymentId, Instant now) {
        JobPayment payment = payments.findById(paymentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "payment_not_found"));
        PaymentGateway.RemoteState remote = gateway.fetchState(payment);
        PaymentStatus before = payment.getStatus();
        if (remote.refunded() && payment.getStatus().canTransitionTo(PaymentStatus.refunded)) {
            payment.transitionTo(PaymentStatus.refunded, now);
        } else if (remote.transferred() && payment.getStatus().canTransitionTo(PaymentStatus.released)) {
            payment.transitionTo(PaymentStatus.released, now);
        } else if (remote.paid() && payment.getStatus().canTransitionTo(PaymentStatus.paid)) {
            payment.transitionTo(PaymentStatus.paid, now);
        }
        payment.setLastReconciledAt(now);
        record(payment, EVENT_RECONCILED, null, before, payment.getStatus(), remote.detail(), now);
        return payments.save(payment);
    }

    @Transactional(readOnly = true)
    public Optional<JobPayment> findByJob(UUID jobId) {
        return payments.findByJobId(jobId);
    }

    @Transactional(readOnly = true)
    public List<JobPayment> pendingRemoteOperations() {
        return payments.findByStatusInOrderByCreatedAtAsc(List.of(
                PaymentStatus.checkout_created, PaymentStatus.payout_requested, PaymentStatus.refund_requested));
    }

    @Transactional(readOnly = true)
    public List<PaymentEvent> history(UUID paymentId) {
        return events.findByPaymentIdOrderByCreatedAtAsc(paymentId);
    }

    public Map<String, Object> toResponse(JobPayment payment) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", payment.getId().toString());
        body.put("provider", payment.getProvider().name());
        body.put("demo", payment.getProvider() == PaymentProvider.demo);
        body.put("status", payment.getStatus().name());
        body.put("currency", payment.getCurrency());
        body.put("amountCents", payment.getAmountCents());
        body.put("platformFeeCents", payment.getPlatformFeeCents());
        body.put("proAmountCents", payment.getProAmountCents());
        body.put("paidAt", stamp(payment.getPaidAt()));
        body.put("payoutRequestedAt", stamp(payment.getPayoutRequestedAt()));
        body.put("releasedAt", stamp(payment.getReleasedAt()));
        body.put("refundedAt", stamp(payment.getRefundedAt()));
        body.put("disputedAt", stamp(payment.getDisputedAt()));
        body.put("lastReconciledAt", stamp(payment.getLastReconciledAt()));
        body.put("failureReason", payment.getFailureReason());
        body.put("transferId", payment.getStripeTransferId());
        body.put("refundId", payment.getStripeRefundId());
        return body;
    }

    private JobPayment requirePayment(UUID jobId) {
        return payments.findByJobId(jobId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "payment_not_found"));
    }

    private PaymentProvider provider() {
        return demo() ? PaymentProvider.demo : PaymentProvider.stripe;
    }

    private void record(JobPayment payment, String type, String providerEventId, PaymentStatus from,
                        PaymentStatus to, String detail, Instant now) {
        events.save(new PaymentEvent(UUID.randomUUID(), payment.getId(), type, providerEventId,
                from == null ? null : from.name(), to == null ? null : to.name(), detail, now));
    }

    private static String stamp(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    public record CheckoutResult(JobPayment payment, String url, boolean paidImmediately) {
    }
}
