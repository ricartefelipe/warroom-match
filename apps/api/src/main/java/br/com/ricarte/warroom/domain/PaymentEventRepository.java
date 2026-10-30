package br.com.ricarte.warroom.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, UUID> {

    boolean existsByProviderEventId(String providerEventId);

    List<PaymentEvent> findByPaymentIdOrderByCreatedAtAsc(UUID paymentId);
}
