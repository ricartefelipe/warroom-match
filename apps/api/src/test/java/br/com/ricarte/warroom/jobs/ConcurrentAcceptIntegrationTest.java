package br.com.ricarte.warroom.jobs;

import br.com.ricarte.warroom.domain.ApplicationRepository;
import br.com.ricarte.warroom.domain.ApplicationStatus;
import br.com.ricarte.warroom.support.ApiClient;
import br.com.ricarte.warroom.support.DatabaseCleaner;
import br.com.ricarte.warroom.support.PostgresIntegrationTest;
import br.com.ricarte.warroom.support.Scenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class ConcurrentAcceptIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    DatabaseCleaner databaseCleaner;
    @Autowired
    JobService jobService;
    @Autowired
    ApplicationRepository applications;

    ApiClient api;
    Scenario scenario;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
        api = new ApiClient(mockMvc, objectMapper);
        scenario = new Scenario(api);
    }

    @Test
    @Timeout(30)
    void twoSimultaneousAcceptsLeaveExactlyOneProMatched() throws Exception {
        String company = api.signup("company");
        String proA = scenario.approvedPro();
        String proB = scenario.approvedPro();
        String jobId = scenario.createJob(company);
        UUID applicationA = UUID.fromString(scenario.apply(proA, jobId));
        UUID applicationB = UUID.fromString(scenario.apply(proB, jobId));
        UUID companyId = UUID.fromString(api.get("/v1/auth/me", company).get("accountId").asText());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Boolean> first = executor.submit(accept(companyId, UUID.fromString(jobId), applicationA, start));
        Future<Boolean> second = executor.submit(accept(companyId, UUID.fromString(jobId), applicationB, start));
        start.countDown();
        int successes = (first.get(20, TimeUnit.SECONDS) ? 1 : 0) + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);
        executor.shutdownNow();

        assertThat(successes).isEqualTo(1);
        long accepted = applications.findByJobId(UUID.fromString(jobId)).stream()
                .filter(a -> a.getStatus() == ApplicationStatus.accepted).count();
        assertThat(accepted).isEqualTo(1);
        assertThat(api.get("/v1/jobs/" + jobId, company).get("status").asText()).isEqualTo("matched");
    }

    private Callable<Boolean> accept(UUID companyId, UUID jobId, UUID applicationId, CountDownLatch start) {
        return () -> {
            start.await(5, TimeUnit.SECONDS);
            try {
                jobService.acceptApplication(companyId, jobId, applicationId);
                return true;
            } catch (RuntimeException ex) {
                return false;
            }
        };
    }
}
