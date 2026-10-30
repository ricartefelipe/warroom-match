package br.com.ricarte.warroom.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VettingEventRepository extends JpaRepository<VettingEvent, UUID> {

    List<VettingEvent> findByAccountIdOrderByCreatedAtAsc(UUID accountId);
}
