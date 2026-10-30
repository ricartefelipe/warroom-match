package br.com.ricarte.warroom.billing;

import br.com.ricarte.warroom.config.BillingConfigValidator;
import br.com.ricarte.warroom.config.WarroomProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingConfigValidatorTest {

    private static WarroomProperties props(WarroomProperties.Environment env, BillingMode mode, String key, String secret) {
        return new WarroomProperties(env,
                new WarroomProperties.Auth("http://a", "http://b", "x@y", 20, 30, false, false, List.of(), 5, 60, 15),
                new WarroomProperties.Billing(mode, 1800, 20000, "brl", key, secret),
                new WarroomProperties.Cors("http://a"),
                new WarroomProperties.Totalrecall(""));
    }

    @Test
    void productionRefusesDemoMode() {
        assertThatThrownBy(() -> new BillingConfigValidator(props(WarroomProperties.Environment.production, BillingMode.demo, "", "")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("demo");
    }

    @Test
    void stripeModeRequiresBothSecrets() {
        assertThatThrownBy(() -> new BillingConfigValidator(props(WarroomProperties.Environment.production, BillingMode.stripe, "sk", "")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new BillingConfigValidator(props(WarroomProperties.Environment.local, BillingMode.stripe, "", "whsec")))
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> new BillingConfigValidator(props(WarroomProperties.Environment.production, BillingMode.stripe, "sk", "whsec")))
                .doesNotThrowAnyException();
    }

    @Test
    void missingModeNeverFallsBackToDemo() {
        assertThatThrownBy(() -> new BillingConfigValidator(props(WarroomProperties.Environment.local, null, "", "")))
                .isInstanceOf(IllegalStateException.class);
    }
}
