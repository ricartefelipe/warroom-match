package br.com.ricarte.warroom.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        String url = System.getenv().getOrDefault(
                "WARROOM_TEST_DB_URL",
                "jdbc:postgresql://127.0.0.1:5436/warroom"
        );
        String user = System.getenv().getOrDefault("WARROOM_TEST_DB_USER", "warroom");
        String password = System.getenv().getOrDefault("WARROOM_TEST_DB_PASSWORD", "warroom");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> user);
        registry.add("spring.datasource.password", () -> password);
    }
}
