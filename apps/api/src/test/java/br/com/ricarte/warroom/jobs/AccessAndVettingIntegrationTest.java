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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@AutoConfigureMockMvc
class AccessAndVettingIntegrationTest extends PostgresIntegrationTest {

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
    void unvettedProCannotApplyAndCannotBeAccepted() throws Exception {
        String company = api.signup("company");
        String pro = api.signup("pro");
        String jobId = scenario.createJob(company);

        assertThat(api.status(post("/v1/jobs/" + jobId + "/apply"), pro, "{\"pitch\":\"eu\"}")).isEqualTo(403);
        api.post("/v1/me/vetting", pro, "{\"evidence\":\"links\"}");
        assertThat(api.status(post("/v1/jobs/" + jobId + "/apply"), pro, "{\"pitch\":\"eu\"}")).isEqualTo(403);
        assertThat(api.get("/v1/me/profile", pro).get("vettingStatus").asText()).isEqualTo("submitted");
    }

    @Test
    void suspendedProLosesEligibilityEvenAfterApplying() throws Exception {
        String company = api.signup("company");
        String pro = scenario.approvedPro();
        String proId = api.get("/v1/auth/me", pro).get("accountId").asText();
        String jobId = scenario.createJob(company);
        String applicationId = scenario.apply(pro, jobId);

        api.post("/v1/admin/vetting/" + proId, scenario.admin(), "{\"decision\":\"suspended\",\"note\":\"denúncia\"}");

        assertThat(api.status(post("/v1/jobs/" + jobId + "/accept/" + applicationId), company, null)).isEqualTo(403);
        JsonNode history = api.get("/v1/admin/vetting/" + proId + "/history", scenario.admin());
        assertThat(history.size()).isEqualTo(3);
        assertThat(history.get(2).get("to").asText()).isEqualTo("suspended");
    }

    @Test
    void suspendedAccountCannotUseItsSession() throws Exception {
        String pro = scenario.approvedPro();
        String proId = api.get("/v1/auth/me", pro).get("accountId").asText();

        api.post("/v1/admin/accounts/" + proId + "/suspend", scenario.admin(), null);

        assertThat(api.status(get("/v1/auth/me"), pro, null)).isEqualTo(401);
    }

    @Test
    void nobodyChoosesTheAdminRoleAndNonAdminsCannotUseSupportEndpoints() throws Exception {
        String someone = api.login("someone-" + System.nanoTime() + "@example.com");
        assertThat(api.status(post("/v1/me/role"), someone, "{\"role\":\"admin\"}")).isEqualTo(400);

        String company = api.signup("company");
        assertThat(api.status(get("/v1/admin/vetting/pending"), company, null)).isEqualTo(403);
        assertThat(api.status(get("/v1/admin/disputes"), company, null)).isEqualTo(403);
        assertThat(api.status(get("/v1/admin/payments/pending"), company, null)).isEqualTo(403);
    }

    @Test
    void otherCompaniesAndOtherProsCannotSeeAJobOrItsChat() throws Exception {
        Scenario.Matched m = scenario.matched();
        String otherCompany = api.signup("company");
        String otherPro = scenario.approvedPro();

        assertThat(api.status(get("/v1/jobs/" + m.jobId()), otherCompany, null)).isEqualTo(403);
        assertThat(api.status(get("/v1/jobs/" + m.jobId() + "/messages"), otherCompany, null)).isEqualTo(403);
        assertThat(api.status(get("/v1/jobs/" + m.jobId() + "/messages"), otherPro, null)).isEqualTo(403);

        api.post("/v1/jobs/" + m.jobId() + "/messages", m.company(), "{\"body\":\"Credenciais vão pelo cofre, não por aqui\"}");
        assertThat(api.get("/v1/jobs/" + m.jobId() + "/messages", m.pro()).size()).isEqualTo(1);
        assertThat(api.get("/v1/jobs/" + m.jobId() + "/messages", scenario.admin()).size()).isEqualTo(1);
        assertThat(api.status(post("/v1/jobs/" + m.jobId() + "/messages"), scenario.admin(), "{\"body\":\"x\"}")).isEqualTo(403);
    }

    @Test
    void chatIsClosedBeforeTheMatchAndRejectsOversizedBodies() throws Exception {
        String company = api.signup("company");
        String pro = scenario.approvedPro();
        String jobId = scenario.createJob(company);
        scenario.apply(pro, jobId);

        assertThat(api.status(get("/v1/jobs/" + jobId + "/messages"), pro, null)).isEqualTo(403);
        Scenario.Matched m = scenario.matched();
        String huge = "{\"body\":\"" + "a".repeat(4001) + "\"}";
        assertThat(api.status(post("/v1/jobs/" + m.jobId() + "/messages"), m.company(), huge)).isEqualTo(400);
    }

    @Test
    void reviewsOnlyAfterCompletionAndOnlyByParticipants() throws Exception {
        Scenario.Matched m = scenario.matched();
        assertThat(api.status(post("/v1/jobs/" + m.jobId() + "/reviews"), m.company(), "{\"rating\":5}")).isEqualTo(409);
        api.post("/v1/jobs/" + m.jobId() + "/checkout", m.company(), null);
        api.post("/v1/jobs/" + m.jobId() + "/start", m.pro(), null);
        api.post("/v1/jobs/" + m.jobId() + "/complete", m.company(), null);
        String stranger = scenario.approvedPro();
        assertThat(api.status(post("/v1/jobs/" + m.jobId() + "/reviews"), stranger, "{\"rating\":5}")).isEqualTo(403);
        assertThat(api.status(post("/v1/jobs/" + m.jobId() + "/reviews"), m.pro(), "{\"rating\":4}")).isEqualTo(200);
    }
}
