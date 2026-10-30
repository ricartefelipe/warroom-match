package br.com.ricarte.warroom.billing;

import br.com.ricarte.warroom.config.WarroomProperties;
import br.com.ricarte.warroom.domain.Job;
import br.com.ricarte.warroom.domain.JobPayment;
import br.com.ricarte.warroom.domain.Profile;
import br.com.ricarte.warroom.web.ApiException;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.Transfer;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.TransferCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.http.HttpStatus;

import java.util.Optional;

/**
 * Stripe adapter: Checkout Session (mode payment) to receive, Connect Transfer to pay the
 * professional's connected account, Refund to return the money. Every call carries an
 * idempotency key derived from the payment so a retry after a timeout never repeats the effect.
 *
 * <p>Transfers need a connected account per professional (Stripe Connect onboarding, done by the
 * owner with Stripe); without it the payout stays requested and is reported as such.
 */
public class StripePaymentGateway implements PaymentGateway {

    static final String METADATA_JOB_ID = "jobId";
    static final String METADATA_PAYMENT_ID = "paymentId";
    private static final String SUCCEEDED = "succeeded";

    private final StripeClient client;
    private final WarroomProperties properties;

    public StripePaymentGateway(WarroomProperties properties) {
        this(new StripeClient(properties.billing().stripeApiKey()), properties);
    }

    StripePaymentGateway(StripeClient client, WarroomProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public Checkout createCheckout(Job job, JobPayment payment) {
        String base = properties.auth().appBaseUrl().replaceAll("/$", "");
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(base + "/app/jobs/" + job.getId())
                .setCancelUrl(base + "/app/jobs/" + job.getId())
                .setClientReferenceId(payment.getId().toString())
                .putMetadata(METADATA_JOB_ID, job.getId().toString())
                .putMetadata(METADATA_PAYMENT_ID, payment.getId().toString())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(payment.getCurrency())
                                .setUnitAmount(payment.getAmountCents())
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(job.getTitle())
                                        .build())
                                .build())
                        .build())
                .build();
        try {
            Session session = client.checkout().sessions().create(params,
                    idempotent("checkout-" + payment.getId() + "-" + payment.getCheckoutAttempts()));
            return new Checkout(session.getId(), session.getUrl(), false);
        } catch (StripeException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "stripe_error");
        }
    }

    @Override
    public Optional<Confirmation> requestPayout(JobPayment payment, Profile professional) {
        if (professional.getStripeAccountId() == null || professional.getStripeAccountId().isBlank()) {
            return Optional.empty();
        }
        TransferCreateParams params = TransferCreateParams.builder()
                .setAmount(payment.getProAmountCents())
                .setCurrency(payment.getCurrency())
                .setDestination(professional.getStripeAccountId())
                .setTransferGroup(payment.getJobId().toString())
                .putMetadata(METADATA_PAYMENT_ID, payment.getId().toString())
                .build();
        try {
            Transfer transfer = client.transfers().create(params, idempotent("transfer-" + payment.getId()));
            return Optional.of(new Confirmation(transfer.getId()));
        } catch (StripeException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "stripe_error");
        }
    }

    @Override
    public Optional<Confirmation> requestRefund(JobPayment payment) {
        if (payment.getStripePaymentIntentId() == null) {
            return Optional.empty();
        }
        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(payment.getStripePaymentIntentId())
                .putMetadata(METADATA_PAYMENT_ID, payment.getId().toString())
                .build();
        try {
            Refund refund = client.refunds().create(params, idempotent("refund-" + payment.getId()));
            boolean done = SUCCEEDED.equals(refund.getStatus());
            return done ? Optional.of(new Confirmation(refund.getId())) : Optional.empty();
        } catch (StripeException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "stripe_error");
        }
    }

    @Override
    public RemoteState fetchState(JobPayment payment) {
        try {
            boolean paid = false;
            if (payment.getStripePaymentIntentId() != null) {
                PaymentIntent intent = client.paymentIntents().retrieve(payment.getStripePaymentIntentId());
                paid = SUCCEEDED.equals(intent.getStatus());
            }
            boolean transferred = payment.getStripeTransferId() != null
                    && client.transfers().retrieve(payment.getStripeTransferId()).getReversed() == Boolean.FALSE;
            boolean refunded = payment.getStripeRefundId() != null
                    && SUCCEEDED.equals(client.refunds().retrieve(payment.getStripeRefundId()).getStatus());
            return new RemoteState(paid, transferred, refunded, "stripe");
        } catch (StripeException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "stripe_error");
        }
    }

    private static RequestOptions idempotent(String key) {
        return RequestOptions.builder().setIdempotencyKey(key).build();
    }
}
