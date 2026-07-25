package br.com.ricarte.warroom.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Optional<Review> findByJobIdAndReviewerAccountId(UUID jobId, UUID reviewerAccountId);
}
