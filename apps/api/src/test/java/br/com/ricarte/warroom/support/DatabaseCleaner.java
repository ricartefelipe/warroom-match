package br.com.ricarte.warroom.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseCleaner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void clean() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE reviews, messages, escrow_payments, applications, jobs,
                profiles, login_tokens, sessions, accounts
                CASCADE
                """);
    }
}
