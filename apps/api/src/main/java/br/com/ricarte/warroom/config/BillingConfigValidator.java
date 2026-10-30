package br.com.ricarte.warroom.config;

import br.com.ricarte.warroom.billing.BillingMode;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Fails closed before any bean exists: production never runs in demo mode, and Stripe mode never
 * starts without the API key and the webhook secret. Registered in META-INF/spring.factories so it
 * runs as soon as the environment is ready, before the database is even touched.
 */
public class BillingConfigValidator implements EnvironmentPostProcessor {

    public BillingConfigValidator() {
    }

    /** Validates already-bound properties; used by the startup hook and by tests. */
    public BillingConfigValidator(WarroomProperties properties) {
        validate(properties.billing(), properties.production());
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Binder binder = Binder.get(environment);
        WarroomProperties.Billing billing = binder.bind("warroom.billing", Bindable.of(WarroomProperties.Billing.class))
                .orElseThrow(() -> new IllegalStateException("warroom.billing configuration is missing"));
        WarroomProperties.Environment env = binder.bind("warroom.environment", WarroomProperties.Environment.class)
                .orElse(WarroomProperties.Environment.local);
        validate(billing, env == WarroomProperties.Environment.production);
    }

    static void validate(WarroomProperties.Billing billing, boolean production) {
        if (billing.mode() == null) {
            throw new IllegalStateException("warroom.billing.mode is required: demo or stripe");
        }
        if (billing.mode() == BillingMode.demo && production) {
            throw new IllegalStateException("warroom.billing.mode=demo is not allowed when warroom.environment=production");
        }
        if (billing.mode() == BillingMode.stripe && !billing.stripeConfigured()) {
            throw new IllegalStateException("warroom.billing.mode=stripe requires STRIPE_API_KEY and STRIPE_WEBHOOK_SECRET");
        }
        if (billing.currency() == null || billing.currency().length() != 3) {
            throw new IllegalStateException("warroom.billing.currency must be a 3-letter ISO 4217 code");
        }
    }
}
