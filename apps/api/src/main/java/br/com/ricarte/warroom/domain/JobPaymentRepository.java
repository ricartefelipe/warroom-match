package br.com.ricarte.warroom.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobPaymentRepository extends JpaRepository<JobPayment, UUID> {

    Optional<JobPayment> findByJobId(UUID jobId);

    Optional<JobPayment> findByStripeSessionId(String stripeSessionId);

    Optional<JobPayment> findByStripePaymentIntentId(String stripePaymentIntentId);

    List<JobPayment> findByStatusInOrderByCreatedAtAsc(List<PaymentStatus> statuses);
}
