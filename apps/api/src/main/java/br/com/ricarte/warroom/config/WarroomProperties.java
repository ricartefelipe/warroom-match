package br.com.ricarte.warroom.config;

import br.com.ricarte.warroom.billing.BillingMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "warroom")
public record WarroomProperties(
        Environment environment,
        Auth auth,
        Billing billing,
        Cors cors,
        Totalrecall totalrecall
) {

    public enum Environment {
        local,
        production
    }

    public boolean production() {
        return environment == Environment.production;
    }

    public record Auth(
            String appBaseUrl,
            String apiBaseUrl,
            String fromEmail,
            int magicLinkTtlMinutes,
            int sessionTtlDays,
            boolean exposeMagicLink,
            boolean trustForwardedHost,
            List<String> adminEmails,
            int magicLinkRequestsPerWindow,
            int magicLinkRequestsPerAddress,
            int magicLinkWindowMinutes
    ) {
        public List<String> adminEmails() {
            return adminEmails == null ? List.of() : adminEmails;
        }
    }

    /**
     * Financial configuration. {@code mode} is explicit: {@code demo} simulates every provider
     * operation and is refused in production; {@code stripe} requires the API key and the webhook
     * secret. Missing keys never turn into a demo.
     */
    public record Billing(
            BillingMode mode,
            int platformFeeBps,
            long minBudgetCents,
            String currency,
            String stripeApiKey,
            String stripeWebhookSecret
    ) {
        public boolean stripeConfigured() {
            return !isBlank(stripeApiKey) && !isBlank(stripeWebhookSecret);
        }

        public boolean demo() {
            return mode == BillingMode.demo;
        }

        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }

    public record Cors(String allowedOrigins) {
    }

    public record Totalrecall(String provisionToken) {
    }
}
