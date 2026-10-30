package br.com.ricarte.warroom.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

    Optional<Dispute> findByJobIdAndStatus(UUID jobId, DisputeStatus status);

    List<Dispute> findByStatusOrderByCreatedAtAsc(DisputeStatus status);
}
