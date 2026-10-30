package br.com.ricarte.warroom.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/** One PostgreSQL container for the whole suite; every cached Spring context points at it. */
@SpringBootTest
@ActiveProfiles("test")
@Import(NoOpMailConfig.class)
public abstract class PostgresIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("warroom")
            .withUsername("warroom")
            .withPassword("warroom");

    static {
        POSTGRES.start();
    }
}
