package br.com.ricarte.warroom.billing;

import br.com.ricarte.warroom.config.WarroomProperties;
import br.com.ricarte.warroom.web.ApiException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/billing/stripe")
public class StripeWebhookController {

    private final WarroomProperties properties;
    private final DemoEscrowService demoEscrowService;

    public StripeWebhookController(WarroomProperties properties, DemoEscrowService demoEscrowService) {
        this.properties = properties;
        this.demoEscrowService = demoEscrowService;
    }

    @PostMapping("/webhook")
    public Map<String, String> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature
    ) {
        String secret = properties.billing().stripeWebhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "stripe_not_configured");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, secret);
        } catch (SignatureVerificationException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_stripe_signature");
        }

        if ("checkout.session.completed".equals(event.getType())) {
            Session session = (Session) event.getDataObjectDeserializer()
                    .getObject()
                    .orElse(null);
            if (session != null && session.getMetadata() != null) {
                String jobIdRaw = session.getMetadata().get("jobId");
                if (jobIdRaw != null) {
                    demoEscrowService.markHeldFromStripe(UUID.fromString(jobIdRaw), session.getId());
                }
            }
        }
        return Map.of("received", "true");
    }
}
