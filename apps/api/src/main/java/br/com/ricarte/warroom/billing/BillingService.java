package br.com.ricarte.warroom.billing;

import br.com.ricarte.warroom.config.WarroomProperties;
import br.com.ricarte.warroom.domain.EscrowPayment;
import br.com.ricarte.warroom.domain.EscrowPaymentRepository;
import br.com.ricarte.warroom.domain.EscrowStatus;
import br.com.ricarte.warroom.domain.Job;
import br.com.ricarte.warroom.web.ApiException;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {

    private final WarroomProperties properties;
    private final DemoEscrowService demoEscrowService;
    private final EscrowPaymentRepository escrowPaymentRepository;

    public BillingService(
            WarroomProperties properties,
            DemoEscrowService demoEscrowService,
            EscrowPaymentRepository escrowPaymentRepository
    ) {
        this.properties = properties;
        this.demoEscrowService = demoEscrowService;
        this.escrowPaymentRepository = escrowPaymentRepository;
    }

    @Transactional
    public Map<String, Object> checkout(Job job) {
        if (demoEscrowService.demoMode()) {
            return demoEscrowService.fundJob(job);
        }
        return createStripeCheckout(job);
    }

    private Map<String, Object> createStripeCheckout(Job job) {
        if (escrowPaymentRepository.findByJobId(job.getId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "escrow_already_exists");
        }

        DemoEscrowService.FeeSplit fees = demoEscrowService.computeFees(job.getBudgetCents());
        Instant now = Instant.now();
        EscrowPayment escrow = new EscrowPayment(
                UUID.randomUUID(),
                job.getId(),
                fees.amountCents(),
                fees.platformFeeCents(),
                fees.proAmountCents(),
                EscrowStatus.pending,
                null,
                now
        );
        escrowPaymentRepository.save(escrow);

        Stripe.apiKey = properties.billing().stripeApiKey();
        String successUrl = properties.auth().appBaseUrl().replaceAll("/$", "")
                + "/jobs/" + job.getId() + "?checkout=success";
        String cancelUrl = properties.auth().appBaseUrl().replaceAll("/$", "")
                + "/jobs/" + job.getId() + "?checkout=cancel";

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .putMetadata("jobId", job.getId().toString())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("brl")
                                .setUnitAmount(job.getBudgetCents())
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(job.getTitle())
                                        .build())
                                .build())
                        .build())
                .build();

        try {
            Session session = Session.create(params);
            escrow.setStripeSessionId(session.getId());
            escrowPaymentRepository.save(escrow);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("demo", false);
            body.put("checkoutUrl", session.getUrl());
            body.put("sessionId", session.getId());
            return body;
        } catch (StripeException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "stripe_error");
        }
    }
}
