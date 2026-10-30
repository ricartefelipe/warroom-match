package br.com.ricarte.warroom.billing;

import br.com.ricarte.warroom.domain.Job;
import br.com.ricarte.warroom.domain.JobPayment;
import br.com.ricarte.warroom.domain.PaymentEventRepository;
import br.com.ricarte.warroom.domain.Profile;
import br.com.ricarte.warroom.support.ApiClient;
import br.com.ricarte.warroom.support.DatabaseCleaner;
import br.com.ricarte.warroom.support.PostgresIntegrationTest;
import br.com.ricarte.warroom.support.Scenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Stripe mode with a stubbed gateway: checkout creation and transfers never leave the process, but
 * the webhook is the real one, with signature verification and the real state machine.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "warroom.billing.mode=stripe",
        "warroom.billing.stripe-api-key=sk_test_fake",
        "warroom.billing.stripe-webhook-secret=" + StripeWebhookIntegrationTest.WEBHOOK_SECRET
})
@Import(StripeWebhookIntegrationTest.StubGateway.class)
class StripeWebhookIntegrationTest extends PostgresIntegrationTest {

    static final String WEBHOOK_SECRET = "whsec_test_0123456789abcdef";

    @TestConfiguration
    static class StubGateway {
        static final AtomicInteger PAYOUTS = new AtomicInteger();

        @Bean
        @Primary
        PaymentGateway stubPaymentGateway() {
            return new PaymentGateway() {
                @Override
                public Checkout createCheckout(Job job, JobPayment payment) {
                    return new Checkout("cs_test_" + payment.getId(), "https://checkout.stripe.test/" + payment.getId(), false);
                }

                @Override
                public Optional<Confirmation> requestPayout(JobPayment payment, Profile professional) {
                    PAYOUTS.incrementAndGet();
                    return professional.getStripeAccountId() == null
                            ? Optional.empty()
                            : Optional.of(new Confirmation("tr_test_" + payment.getId()));
                }

                @Override
                public Optional<Confirmation> requestRefund(JobPayment payment) {
                    return Optional.of(new Confirmation("re_test_" + payment.getId()));
                }

                @Override
                public RemoteState fetchState(JobPayment payment) {
                    return new RemoteState(payment.getPaidAt() != null, false, false, "stub");
                }
            };
        }
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    DatabaseCleaner databaseCleaner;
    @Autowired
    PaymentEventRepository paymentEvents;

    ApiClient api;
    Scenario scenario;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
        api = new ApiClient(mockMvc, objectMapper);
        scenario = new Scenario(api);
    }

    @Test
    void checkoutReturnsProviderUrlAndJobStaysMatchedUntilTheWebhook() throws Exception {
        Scenario.Matched m = scenario.matched();
        JsonNode checkout = api.post("/v1/jobs/" + m.jobId() + "/checkout", m.company(), null);

        assertThat(checkout.get("demo").asBoolean()).isFalse();
        assertThat(checkout.get("url").asText()).startsWith("https://checkout.stripe.test/");
        assertThat(checkout.get("status").asText()).isEqualTo("checkout_created");
        assertThat(checkout.get("jobStatus").asText()).isEqualTo("matched");
        JsonNode again = api.post("/v1/jobs/" + m.jobId() + "/checkout", m.company(), null);
        assertThat(again.get("paymentId").asText()).isEqualTo(checkout.get("paymentId").asText());
    }

    @Test
    void verifiedPaidEventFundsTheJobOnceAndDuplicatesAreIgnored() throws Exception {
        Scenario.Matched m = scenario.matched();
        JsonNode checkout = api.post("/v1/jobs/" + m.jobId() + "/checkout", m.company(), null);
        String sessionId = "cs_test_" + checkout.get("paymentId").asText();

        String payload = sessionCompleted("evt_1", sessionId, m.jobId(), 50000, "brl", "paid");
        assertThat(webhook(payload, sign(payload))).isEqualTo(200);
        assertThat(api.get("/v1/jobs/" + m.jobId(), m.company()).get("status").asText()).isEqualTo("funded");
        assertThat(api.get("/v1/jobs/" + m.jobId(), m.company()).get("payment").get("status").asText()).isEqualTo("paid");

        assertThat(webhook(payload, sign(payload))).isEqualTo(200);
        assertThat(paymentEvents.existsByProviderEventId("evt_1")).isTrue();
        assertThat(paymentEvents.count()).isEqualTo(2);
    }

    @Test
    void invalidSignatureWrongAmountAndUnpaidSessionNeverFund() throws Exception {
        Scenario.Matched m = scenario.matched();
        JsonNode checkout = api.post("/v1/jobs/" + m.jobId() + "/checkout", m.company(), null);
        String sessionId = "cs_test_" + checkout.get("paymentId").asText();

        String good = sessionCompleted("evt_sig", sessionId, m.jobId(), 50000, "brl", "paid");
        assertThat(webhook(good, "t=1,v1=deadbeef")).isEqualTo(400);
        assertThat(webhook(good, null)).isEqualTo(400);

        String unpaid = sessionCompleted("evt_unpaid", sessionId, m.jobId(), 50000, "brl", "unpaid");
        assertThat(webhook(unpaid, sign(unpaid))).isEqualTo(200);
        assertThat(api.get("/v1/jobs/" + m.jobId(), m.company()).get("status").asText()).isEqualTo("matched");

        String wrongAmount = sessionCompleted("evt_amount", sessionId, m.jobId(), 4999900, "brl", "paid");
        assertThat(webhook(wrongAmount, sign(wrongAmount))).isEqualTo(200);
        JsonNode job = api.get("/v1/jobs/" + m.jobId(), m.company());
        assertThat(job.get("status").asText()).isEqualTo("matched");
        assertThat(job.get("payment").get("status").asText()).isEqualTo("failed");
    }

    @Test
    void lateEventAfterReleaseDoesNotRegressTheJobOrPayTwice() throws Exception {
        Scenario.Matched m = scenario.matched();
        JsonNode checkout = api.post("/v1/jobs/" + m.jobId() + "/checkout", m.company(), null);
        String sessionId = "cs_test_" + checkout.get("paymentId").asText();
        String paid = sessionCompleted("evt_first", sessionId, m.jobId(), 50000, "brl", "paid");
        webhook(paid, sign(paid));
        api.post("/v1/jobs/" + m.jobId() + "/start", m.pro(), null);
        JsonNode completed = api.post("/v1/jobs/" + m.jobId() + "/complete", m.company(), null);
        assertThat(completed.get("payment").get("status").asText()).isEqualTo("payout_requested");
        assertThat(completed.get("payment").get("failureReason").asText()).isEqualTo("pro_not_onboarded");
        int payoutsBefore = StubGateway.PAYOUTS.get();

        String late = sessionCompleted("evt_late", sessionId, m.jobId(), 50000, "brl", "paid");
        assertThat(webhook(late, sign(late))).isEqualTo(200);

        JsonNode job = api.get("/v1/jobs/" + m.jobId(), m.company());
        assertThat(job.get("status").asText()).isEqualTo("completed");
        assertThat(job.get("payment").get("status").asText()).isEqualTo("payout_requested");
        assertThat(StubGateway.PAYOUTS.get()).isEqualTo(payoutsBefore);
        assertThat(api.status(post("/v1/jobs/" + m.jobId() + "/complete"), m.company(), null)).isEqualTo(409);
    }

    @Test
    void expiredSessionAllowsANewCheckoutWithoutDuplicatingThePayment() throws Exception {
        Scenario.Matched m = scenario.matched();
        JsonNode first = api.post("/v1/jobs/" + m.jobId() + "/checkout", m.company(), null);
        String sessionId = "cs_test_" + first.get("paymentId").asText();
        String expired = sessionEvent("evt_exp", "checkout.session.expired", sessionId, m.jobId(), 50000, "brl", "unpaid");
        assertThat(webhook(expired, sign(expired))).isEqualTo(200);

        JsonNode second = api.post("/v1/jobs/" + m.jobId() + "/checkout", m.company(), null);
        assertThat(second.get("paymentId").asText()).isEqualTo(first.get("paymentId").asText());
        assertThat(second.get("status").asText()).isEqualTo("checkout_created");
    }

    @Test
    void demoCheckoutIsImpossibleInStripeMode() throws Exception {
        JsonNode config = api.get("/v1/config", null);
        assertThat(config.get("billingMode").asText()).isEqualTo("stripe");
        assertThat(config.get("demo").asBoolean()).isFalse();
    }

    private int webhook(String payload, String signature) throws Exception {
        var request = post("/v1/billing/stripe/webhook");
        if (signature != null) {
            request.header("Stripe-Signature", signature);
        }
        return api.perform(request, null, payload).getResponse().getStatus();
    }

    private static String sessionCompleted(String eventId, String sessionId, String jobId, long amount, String currency, String paymentStatus) {
        return sessionEvent(eventId, "checkout.session.completed", sessionId, jobId, amount, currency, paymentStatus);
    }

    private static String sessionEvent(String eventId, String type, String sessionId, String jobId, long amount,
                                       String currency, String paymentStatus) {
        return """
                {"id":"%s","object":"event","api_version":"2024-06-20","type":"%s","created":1700000000,
                 "data":{"object":{"id":"%s","object":"checkout.session","mode":"payment","status":"complete",
                 "payment_status":"%s","amount_total":%d,"currency":"%s","payment_intent":"pi_%s",
                 "metadata":{"jobId":"%s"}}}}
                """.formatted(eventId, type, sessionId, paymentStatus, amount, currency, UUID.randomUUID(), jobId);
    }

    /** Same scheme Stripe uses: HMAC-SHA256 of "timestamp.payload" with the endpoint secret. */
    private static String sign(String payload) throws Exception {
        long timestamp = System.currentTimeMillis() / 1000;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
        return "t=" + timestamp + ",v1=" + signature;
    }
}
