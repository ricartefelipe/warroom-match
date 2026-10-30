package br.com.ricarte.warroom.auth;

import br.com.ricarte.warroom.support.ApiClient;
import br.com.ricarte.warroom.support.DatabaseCleaner;
import br.com.ricarte.warroom.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@AutoConfigureMockMvc
class MagicLinkIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    DatabaseCleaner databaseCleaner;

    ApiClient api;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
        api = new ApiClient(mockMvc, objectMapper);
    }

    @Test
    void magicLinkIsSingleUseEvenUnderConcurrentVerification() throws Exception {
        JsonNode magic = api.post("/v1/auth/magic-link", null, "{\"email\":\"once@example.com\",\"name\":\"Once\"}");
        String token = magic.get("magicLink").asText().split("token=")[1];

        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = executor.invokeAll(List.of(verify(token, start), verify(token, start), verify(token, start), verify(token, start)));
        start.countDown();
        int ok = 0;
        for (Future<Integer> result : results) {
            if (result.get(10, TimeUnit.SECONDS) == 200) {
                ok++;
            }
        }
        executor.shutdownNow();

        assertThat(ok).isEqualTo(1);
        assertThat(api.status(post("/v1/auth/verify"), null, "{\"token\":\"" + token + "\"}")).isEqualTo(401);
    }

    @Test
    void logoutInvalidatesTheSession() throws Exception {
        String session = api.login("logout@example.com");
        assertThat(api.status(get("/v1/auth/me"), session, null)).isEqualTo(200);
        api.post("/v1/auth/logout", session, null);
        assertThat(api.status(get("/v1/auth/me"), session, null)).isEqualTo(401);
    }

    @Test
    void configuredSupportEmailBecomesAdminAndOthersDoNot() throws Exception {
        String admin = api.login("support@warroom.test");
        assertThat(api.get("/v1/auth/me", admin).get("role").asText()).isEqualTo("admin");
        String regular = api.login("regular@example.com");
        assertThat(api.get("/v1/auth/me", regular).get("role").isNull()).isTrue();
    }

    private Callable<Integer> verify(String token, CountDownLatch start) {
        return () -> {
            start.await(5, TimeUnit.SECONDS);
            return api.status(post("/v1/auth/verify"), null, "{\"token\":\"" + token + "\"}");
        };
    }
}
