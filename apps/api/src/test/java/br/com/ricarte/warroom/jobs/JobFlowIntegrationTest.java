package br.com.ricarte.warroom.jobs;

import br.com.ricarte.warroom.support.ApiClient;
import br.com.ricarte.warroom.support.DatabaseCleaner;
import br.com.ricarte.warroom.support.PostgresIntegrationTest;
import br.com.ricarte.warroom.support.Scenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@AutoConfigureMockMvc
class JobFlowIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    DatabaseCleaner databaseCleaner;

    ApiClient api;
    Scenario scenario;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
        api = new ApiClient(mockMvc, objectMapper);
        scenario = new Scenario(api);
    }

    @Test
    void demoFlowPaysDeliversCompletesAndReleasesWithFeeSplit() throws Exception {
        Scenario.Matched m = scenario.matched();

        JsonNode checkout = api.post("/v1/jobs/" + m.jobId() + "/checkout", m.company(), null);
        assertThat(checkout.get("demo").asBoolean()).isTrue();
        assertThat(checkout.get("status").asText()).isEqualTo("paid");
        assertThat(checkout.get("jobStatus").asText()).isEqualTo("funded");
        assertThat(checkout.get("payment").get("platformFeeCents").asLong()).isEqualTo(9000L);
        assertThat(checkout.get("payment").get("proAmountCents").asLong()).isEqualTo(41000L);

        assertThat(api.post("/v1/jobs/" + m.jobId() + "/start", m.pro(), null).get("status").asText()).isEqualTo("in_progress");
        assertThat(api.post("/v1/jobs/" + m.jobId() + "/deliver", m.pro(), null).get("status").asText()).isEqualTo("delivered");

        JsonNode completed = api.post("/v1/jobs/" + m.jobId() + "/complete", m.company(), null);
        assertThat(completed.get("status").asText()).isEqualTo("completed");
        assertThat(completed.get("payment").get("status").asText()).isEqualTo("released");
        assertThat(completed.get("payment").get("demo").asBoolean()).isTrue();
        assertThat(completed.get("payment").get("transferId").asText()).startsWith("demo_");

        JsonNode review = api.post("/v1/jobs/" + m.jobId() + "/reviews", m.company(), "{\"rating\":5,\"comment\":\"ok\"}");
        assertThat(review.get("rating").asInt()).isEqualTo(5);
    }

    @Test
    void doubleCheckoutDoesNotChargeTwice() throws Exception {
        Scenario.Matched m = scenario.matched();
        api.post("/v1/jobs/" + m.jobId() + "/checkout", m.company(), null);

        assertThat(api.status(post("/v1/jobs/" + m.jobId() + "/checkout"), m.company(), null)).isEqualTo(409);
        JsonNode job = api.get("/v1/jobs/" + m.jobId(), m.company());
        assertThat(job.get("payment").get("amountCents").asLong()).isEqualTo(50000L);
    }

    @Test
    void jobCannotStartBeforePaymentIsConfirmed() throws Exception {
        Scenario.Matched m = scenario.matched();
        assertThat(api.status(post("/v1/jobs/" + m.jobId() + "/start"), m.pro(), null)).isEqualTo(409);
    }

    @Test
    void completedJobNeverGoesBackAndCannotBeDisputedAfterRelease() throws Exception {
        Scenario.Matched m = scenario.matched();
        api.post("/v1/jobs/" + m.jobId() + "/checkout", m.company(), null);
        api.post("/v1/jobs/" + m.jobId() + "/start", m.pro(), null);
        api.post("/v1/jobs/" + m.jobId() + "/complete", m.company(), null);

        assertThat(api.status(post("/v1/jobs/" + m.jobId() + "/start"), m.pro(), null)).isEqualTo(409);
        assertThat(api.status(post("/v1/jobs/" + m.jobId() + "/dispute"), m.pro(), "{\"reason\":\"tarde demais\"}")).isEqualTo(409);
        assertThat(api.status(post("/v1/jobs/" + m.jobId() + "/cancel"), m.company(), null)).isEqualTo(409);
    }

    @Test
    void disputeBlocksReleaseUntilSupportDecides() throws Exception {
        Scenario.Matched m = scenario.matched();
        api.post("/v1/jobs/" + m.jobId() + "/checkout", m.company(), null);
        api.post("/v1/jobs/" + m.jobId() + "/start", m.pro(), null);

        JsonNode disputed = api.post("/v1/jobs/" + m.jobId() + "/dispute", m.company(), "{\"reason\":\"entrega parcial\"}");
        assertThat(disputed.get("status").asText()).isEqualTo("disputed");
        assertThat(disputed.get("payment").get("status").asText()).isEqualTo("disputed");
        assertThat(api.status(post("/v1/jobs/" + m.jobId() + "/complete"), m.company(), null)).isEqualTo(409);

        String admin = scenario.admin();
        assertThat(api.get("/v1/admin/disputes", admin).size()).isEqualTo(1);
        JsonNode resolved = api.post("/v1/admin/disputes/" + m.jobId() + "/resolve", admin,
                "{\"outcome\":\"refund\",\"note\":\"entrega não comprovada\"}");
        assertThat(resolved.get("status").asText()).isEqualTo("cancelled");
        assertThat(resolved.get("payment").get("status").asText()).isEqualTo("refunded");
        assertThat(resolved.get("dispute").get("status").asText()).isEqualTo("resolved_refund");
    }

    @Test
    void cancellingAPaidJobRefundsTheCompany() throws Exception {
        Scenario.Matched m = scenario.matched();
        api.post("/v1/jobs/" + m.jobId() + "/checkout", m.company(), null);

        JsonNode cancelled = api.post("/v1/jobs/" + m.jobId() + "/cancel", m.company(), null);
        assertThat(cancelled.get("status").asText()).isEqualTo("cancelled");
        assertThat(cancelled.get("payment").get("status").asText()).isEqualTo("refunded");
    }

    @Test
    void applicationsAfterTheDeadlineAreRefused() throws Exception {
        String company = api.signup("company");
        String pro = scenario.approvedPro();
        JsonNode job = api.post("/v1/jobs", company, """
                {"title":"Prazo","description":"x","urgency":"p3","budgetCents":20000,
                 "responseDeadlineAt":"2099-01-01T00:00:00Z"}
                """);
        String jobId = job.get("id").asText();
        assertThat(api.status(post("/v1/jobs/" + jobId + "/apply"), pro, "{\"pitch\":\"ok\"}")).isEqualTo(200);
        assertThat(api.status(post("/v1/jobs"), company, """
                {"title":"Passado","description":"x","urgency":"p3","budgetCents":20000,
                 "responseDeadlineAt":"2000-01-01T00:00:00Z"}
                """)).isEqualTo(400);
    }

    @Test
    void publicConfigSaysDemo() throws Exception {
        JsonNode config = api.get("/v1/config", null);
        assertThat(config.get("billingMode").asText()).isEqualTo("demo");
        assertThat(config.get("demo").asBoolean()).isTrue();
    }
}
