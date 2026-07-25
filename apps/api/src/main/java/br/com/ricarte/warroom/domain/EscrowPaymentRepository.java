package br.com.ricarte.warroom.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EscrowPaymentRepository extends JpaRepository<EscrowPayment, UUID> {

    Optional<EscrowPayment> findByJobId(UUID jobId);

    Optional<EscrowPayment> findByStripeSessionId(String stripeSessionId);
}
