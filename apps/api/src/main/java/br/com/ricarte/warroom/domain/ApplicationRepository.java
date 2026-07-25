package br.com.ricarte.warroom.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    List<Application> findByJobId(UUID jobId);

    Optional<Application> findByJobIdAndProAccountId(UUID jobId, UUID proAccountId);

    boolean existsByJobIdAndProAccountId(UUID jobId, UUID proAccountId);
}
