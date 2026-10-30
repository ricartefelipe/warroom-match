package br.com.ricarte.warroom.billing;

import br.com.ricarte.warroom.config.WarroomProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentGatewayConfig {

    @Bean
    PaymentGateway paymentGateway(WarroomProperties properties) {
        return properties.billing().demo()
                ? new DemoPaymentGateway()
                : new StripePaymentGateway(properties);
    }
}
