package br.com.ricarte.warroom.billing;

import br.com.ricarte.warroom.config.WarroomProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Public, non-secret description of the running mode so every screen can say what money is. */
@RestController
@RequestMapping("/v1/config")
public class BillingConfigController {

    private final WarroomProperties properties;

    public BillingConfigController(WarroomProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    public Map<String, Object> config() {
        return Map.of(
                "environment", properties.environment().name(),
                "billingMode", properties.billing().mode().name(),
                "demo", properties.billing().demo(),
                "platformFeeBps", properties.billing().platformFeeBps(),
                "minBudgetCents", properties.billing().minBudgetCents(),
                "currency", properties.billing().currency()
        );
    }
}
