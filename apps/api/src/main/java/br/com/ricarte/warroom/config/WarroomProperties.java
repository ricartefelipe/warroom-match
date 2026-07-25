package br.com.ricarte.warroom.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "warroom")
public record WarroomProperties(
        Auth auth,
        Billing billing,
        Cors cors
) {
    public record Auth(
            String appBaseUrl,
            String apiBaseUrl,
            String fromEmail,
            int magicLinkTtlMinutes,
            int sessionTtlDays,
            boolean exposeMagicLink,
            boolean trustForwardedHost
    ) {
    }

    public record Billing(
            int platformFeeBps,
            long minBudgetCents,
            boolean demoEscrow,
            String stripeApiKey,
            String stripeWebhookSecret
    ) {
        public boolean stripeConfigured() {
            return stripeApiKey != null && !stripeApiKey.isBlank();
        }
    }

    public record Cors(String allowedOrigins) {
    }
}
