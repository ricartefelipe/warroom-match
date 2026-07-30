package br.com.ricarte.warroom.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ricarte.warroom.support.DatabaseCleaner;
import br.com.ricarte.warroom.support.NoOpMailConfig;
import br.com.ricarte.warroom.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@Import(NoOpMailConfig.class)
class JobFlowIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
    }

    @Test
    void fullJobFlowWithDemoEscrowAndFeeSplit() throws Exception {
        String companyToken = signupWithRole("company-" + UUID.randomUUID() + "@example.com", "company");
        String proToken = signupWithRole("pro-" + UUID.randomUUID() + "@example.com", "pro");

        MvcResult createJob = mockMvc.perform(post("/v1/jobs")
                        .header("Authorization", "Bearer " + companyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Incidente API",
                                  "description": "Precisamos de backend senior urgente",
                                  "urgency": "p1",
                                  "budgetCents": 50000,
                                  "skillsNeeded": "java,spring"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("open"))
                .andReturn();
        String jobId = objectMapper.readTree(createJob.getResponse().getContentAsString())
                .get("jobId").asText();

        MvcResult applyResult = mockMvc.perform(post("/v1/jobs/" + jobId + "/apply")
                        .header("Authorization", "Bearer " + proToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pitch\":\"Posso começar agora\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String applicationId = objectMapper.readTree(applyResult.getResponse().getContentAsString())
                .get("applicationId").asText();

        mockMvc.perform(post("/v1/jobs/" + jobId + "/accept/" + applicationId)
                        .header("Authorization", "Bearer " + companyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("matched"));

        mockMvc.perform(post("/v1/jobs/" + jobId + "/checkout")
                        .header("Authorization", "Bearer " + companyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.status").value("held"))
                .andExpect(jsonPath("$.platformFeeCents").value(9000))
                .andExpect(jsonPath("$.proAmountCents").value(41000));

        mockMvc.perform(post("/v1/jobs/" + jobId + "/start")
                        .header("Authorization", "Bearer " + proToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("in_progress"));

        MvcResult complete = mockMvc.perform(post("/v1/jobs/" + jobId + "/complete")
                        .header("Authorization", "Bearer " + companyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.escrow.status").value("released"))
                .andExpect(jsonPath("$.escrow.platformFeeCents").value(9000))
                .andExpect(jsonPath("$.escrow.proAmountCents").value(41000))
                .andReturn();

        JsonNode completeJson = objectMapper.readTree(complete.getResponse().getContentAsString());
        assertThat(completeJson.get("escrow").get("platformFeeCents").asLong()).isEqualTo(9000L);
        assertThat(completeJson.get("escrow").get("proAmountCents").asLong()).isEqualTo(41000L);
        assertThat(completeJson.get("escrow").get("amountCents").asLong()).isEqualTo(50000L);

        mockMvc.perform(get("/v1/jobs/" + jobId)
                        .header("Authorization", "Bearer " + companyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.escrow.status").value("released"));
    }

    private String signupWithRole(String email, String role) throws Exception {
        MvcResult magic = mockMvc.perform(post("/v1/auth/magic-link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"name\":\"Test User\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode magicJson = objectMapper.readTree(magic.getResponse().getContentAsString());
        String link = magicJson.get("magicLink").asText();
        String token = link.substring(link.indexOf("token=") + 6);

        MvcResult verify = mockMvc.perform(post("/v1/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String sessionToken = objectMapper.readTree(verify.getResponse().getContentAsString())
                .get("sessionToken").asText();

        mockMvc.perform(get("/v1/auth/me")
                        .header("Authorization", "Bearer " + sessionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value(nullValue()));

        mockMvc.perform(post("/v1/me/role")
                        .header("Authorization", "Bearer " + sessionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"" + role + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value(role));

        mockMvc.perform(get("/v1/auth/me")
                        .header("Authorization", "Bearer " + sessionToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value(role));

        return sessionToken;
    }
}
