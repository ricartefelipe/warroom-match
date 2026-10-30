package br.com.ricarte.warroom.billing;

import br.com.ricarte.warroom.config.WarroomProperties;
import br.com.ricarte.warroom.domain.JobPayment;
import br.com.ricarte.warroom.domain.JobPaymentRepository;
import br.com.ricarte.warroom.domain.PaymentEvent;
import br.com.ricarte.warroom.domain.PaymentEventRepository;
import br.com.ricarte.warroom.domain.PaymentStatus;
import br.com.ricarte.warroom.jobs.JobService;
import br.com.ricarte.warroom.web.ApiException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Charge;
import com.stripe.model.Dispute;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Stripe events. Signature is mandatory; each event id is processed once; a payment only moves
 * when the session matches the stored one and the paid amount and currency equal the job's.
 * Late or repeated events never regress a job or release money twice.
 */
@RestController
@RequestMapping("/v1/billing/stripe")
public class StripeWebhookController {

    static final String SESSION_COMPLETED = "checkout.session.completed";
    static final String ASYNC_SUCCEEDED = "checkout.session.async_payment_succeeded";
    static final String ASYNC_FAILED = "checkout.session.async_payment_failed";
    static final String SESSION_EXPIRED = "checkout.session.expired";
    static final String CHARGE_REFUNDED = "charge.refunded";
    static final String DISPUTE_CREATED = "charge.dispute.created";
    private static final String PAID = "paid";

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final WarroomProperties properties;
    private final PaymentService paymentService;
    private final JobService jobService;
    private final JobPaymentRepository payments;
    private final PaymentEventRepository events;

    public StripeWebhookController(WarroomProperties properties, PaymentService paymentService, JobService jobService,
                                   JobPaymentRepository payments, PaymentEventRepository events) {
        this.properties = properties;
        this.paymentService = paymentService;
        this.jobService = jobService;
        this.payments = payments;
        this.events = events;
    }

    @PostMapping("/webhook")
    @Transactional
    public Map<String, String> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature
    ) {
        if (properties.billing().demo()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "stripe_not_configured");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature == null ? "" : signature, properties.billing().stripeWebhookSecret());
        } catch (SignatureVerificationException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_stripe_signature");
        }
        if (events.existsByProviderEventId(event.getId())) {
            return Map.of("received", "true", "outcome", "duplicate");
        }
        Instant now = Instant.now();
        String outcome = switch (event.getType()) {
            case SESSION_COMPLETED, ASYNC_SUCCEEDED -> onSessionPaid(event, now);
            case ASYNC_FAILED -> onSessionOutcome(event, PaymentStatus.failed, now);
            case SESSION_EXPIRED -> onSessionOutcome(event, PaymentStatus.expired, now);
            case CHARGE_REFUNDED -> onChargeRefunded(event, now);
            case DISPUTE_CREATED -> onDisputeCreated(event, now);
            default -> "ignored";
        };
        if (!events.existsByProviderEventId(event.getId())) {
            events.save(new PaymentEvent(UUID.randomUUID(), null, event.getType(), event.getId(), null, null, outcome, now));
        }
        return Map.of("received", "true", "outcome", outcome);
    }

    private String onSessionPaid(Event event, Instant now) {
        Optional<Session> session = object(event, Session.class);
        if (session.isEmpty()) {
            return "unreadable";
        }
        Session s = session.get();
        if (!PAID.equals(s.getPaymentStatus())) {
            return "not_paid";
        }
        Optional<JobPayment> found = payments.findByStripeSessionId(s.getId());
        if (found.isEmpty()) {
            log.warn("stripe session {} without local payment", s.getId());
            return "unknown_session";
        }
        JobPayment payment = found.get();
        if (!payment.matchesAmount(s.getAmountTotal(), s.getCurrency())
                || !payment.getJobId().toString().equals(metadata(s, StripePaymentGateway.METADATA_JOB_ID))) {
            paymentService.markCheckoutOutcome(payment, PaymentStatus.failed, event.getId(),
                    "amount, currency or job mismatch on session " + s.getId(), now);
            return "mismatch";
        }
        boolean changed = paymentService.confirmPaid(payment, event.getId(), s.getPaymentIntent(), now);
        if (changed) {
            jobService.onPaymentConfirmed(payment.getJobId(), now);
        }
        return changed ? "paid" : "unchanged";
    }

    private String onSessionOutcome(Event event, PaymentStatus outcome, Instant now) {
        Optional<Session> session = object(event, Session.class);
        if (session.isEmpty()) {
            return "unreadable";
        }
        Optional<JobPayment> found = payments.findByStripeSessionId(session.get().getId());
        if (found.isEmpty()) {
            return "unknown_session";
        }
        paymentService.markCheckoutOutcome(found.get(), outcome, event.getId(), event.getType(), now);
        return outcome.name();
    }

    private String onChargeRefunded(Event event, Instant now) {
        Optional<Charge> charge = object(event, Charge.class);
        if (charge.isEmpty() || charge.get().getPaymentIntent() == null) {
            return "unreadable";
        }
        Optional<JobPayment> found = payments.findByStripePaymentIntentId(charge.get().getPaymentIntent());
        if (found.isEmpty()) {
            return "unknown_payment";
        }
        JobPayment payment = found.get();
        if (payment.getStatus().canTransitionTo(PaymentStatus.refund_requested)) {
            paymentService.markCheckoutOutcome(payment, PaymentStatus.refund_requested, event.getId(), "charge.refunded", now);
        }
        paymentService.reconcile(payment.getId(), now);
        return "refund_recorded";
    }

    private String onDisputeCreated(Event event, Instant now) {
        Optional<Dispute> dispute = object(event, Dispute.class);
        if (dispute.isEmpty() || dispute.get().getPaymentIntent() == null) {
            return "unreadable";
        }
        Optional<JobPayment> found = payments.findByStripePaymentIntentId(dispute.get().getPaymentIntent());
        if (found.isEmpty()) {
            return "unknown_payment";
        }
        paymentService.markDisputedByProvider(found.get(), event.getId(), "charge.dispute.created", now);
        jobService.onProviderDispute(found.get().getJobId(), now);
        return "disputed";
    }

    private static <T extends StripeObject> Optional<T> object(Event event, Class<T> type) {
        return event.getDataObjectDeserializer().getObject()
                .filter(type::isInstance)
                .map(type::cast);
    }

    private static String metadata(Session session, String key) {
        return session.getMetadata() == null ? null : session.getMetadata().get(key);
    }
}
