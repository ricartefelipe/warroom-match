package br.com.ricarte.warroom.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

    List<Job> findByCompanyAccountIdOrderByCreatedAtDesc(UUID companyAccountId);

    List<Job> findByStatusOrderByCreatedAtDesc(JobStatus status);

    List<Job> findAllByOrderByCreatedAtDesc();

    @Query("""
            SELECT j FROM Job j
            WHERE j.matchedProAccountId = :accountId
               OR j.id IN (
                   SELECT a.jobId FROM Application a WHERE a.proAccountId = :accountId
               )
            ORDER BY j.createdAt DESC
            """)
    List<Job> findProJobs(@Param("accountId") UUID accountId);
}
