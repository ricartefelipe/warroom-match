package br.com.ricarte.warroom.support;

import tools.jackson.databind.JsonNode;

/** Reusable steps of the marketplace flow, so each test states only what it checks. */
public final class Scenario {

    public static final String ADMIN_EMAIL = "support@warroom.test";

    private final ApiClient api;

    public Scenario(ApiClient api) {
        this.api = api;
    }

    public String admin() throws Exception {
        return api.login(ADMIN_EMAIL);
    }

    public String approvedPro() throws Exception {
        String pro = api.signup("pro");
        String proId = api.get("/v1/auth/me", pro).get("accountId").asText();
        api.post("/v1/me/vetting", pro, "{\"evidence\":\"github.com/pro, 8 anos de backend\"}");
        api.post("/v1/admin/vetting/" + proId, admin(), "{\"decision\":\"approved\",\"note\":\"referências conferidas\"}");
        return pro;
    }

    public String createJob(String company) throws Exception {
        return createJob(company, 50000);
    }

    public String createJob(String company, long budgetCents) throws Exception {
        JsonNode job = api.post("/v1/jobs", company, """
                {"title":"Incidente API","description":"Backend senior urgente","urgency":"p1",
                 "budgetCents":%d,"skillsNeeded":"java,spring"}
                """.formatted(budgetCents));
        return job.get("id").asText();
    }

    public String apply(String pro, String jobId) throws Exception {
        return api.post("/v1/jobs/" + jobId + "/apply", pro, "{\"pitch\":\"Posso começar agora\"}").get("id").asText();
    }

    /** Company creates, approved pro applies, company accepts: job matched. */
    public Matched matched() throws Exception {
        String company = api.signup("company");
        String pro = approvedPro();
        String jobId = createJob(company);
        String applicationId = apply(pro, jobId);
        api.post("/v1/jobs/" + jobId + "/accept/" + applicationId, company, null);
        return new Matched(company, pro, jobId);
    }

    public record Matched(String company, String pro, String jobId) {
    }
}
