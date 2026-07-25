package br.com.ricarte.warroom.jobs;

import br.com.ricarte.warroom.auth.AuthService;
import br.com.ricarte.warroom.billing.BillingService;
import br.com.ricarte.warroom.billing.DemoEscrowService;
import br.com.ricarte.warroom.config.WarroomProperties;
import br.com.ricarte.warroom.domain.Account;
import br.com.ricarte.warroom.domain.AccountRole;
import br.com.ricarte.warroom.domain.Application;
import br.com.ricarte.warroom.domain.ApplicationRepository;
import br.com.ricarte.warroom.domain.ApplicationStatus;
import br.com.ricarte.warroom.domain.EscrowPayment;
import br.com.ricarte.warroom.domain.EscrowPaymentRepository;
import br.com.ricarte.warroom.domain.EscrowStatus;
import br.com.ricarte.warroom.domain.Job;
import br.com.ricarte.warroom.domain.JobRepository;
import br.com.ricarte.warroom.domain.JobStatus;
import br.com.ricarte.warroom.web.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobService {

    private final AuthService authService;
    private final WarroomProperties properties;
    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final EscrowPaymentRepository escrowPaymentRepository;
    private final BillingService billingService;
    private final DemoEscrowService demoEscrowService;

    public JobService(
            AuthService authService,
            WarroomProperties properties,
            JobRepository jobRepository,
            ApplicationRepository applicationRepository,
            EscrowPaymentRepository escrowPaymentRepository,
            BillingService billingService,
            DemoEscrowService demoEscrowService
    ) {
        this.authService = authService;
        this.properties = properties;
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
        this.escrowPaymentRepository = escrowPaymentRepository;
        this.billingService = billingService;
        this.demoEscrowService = demoEscrowService;
    }

    @Transactional
    public Map<String, Object> createJob(UUID accountId, CreateJobRequest request) {
        Account account = requireRole(accountId, AccountRole.company);
        validateBudget(request.budgetCents());
        validateUrgency(request.urgency());

        Instant now = Instant.now();
        Job job = new Job(
                UUID.randomUUID(),
                account.getId(),
                request.title(),
                request.description(),
                request.urgency(),
                request.budgetCents(),
                request.skillsNeeded(),
                JobStatus.open,
                request.responseDeadlineAt(),
                now,
                now
        );
        jobRepository.save(job);
        return toJobResponse(job);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listJobs(UUID accountId, String scope) {
        Account account = authService.requireAccount(accountId);
        if ("open".equals(scope)) {
            if (account.getRole() != AccountRole.pro) {
                throw new ApiException(HttpStatus.FORBIDDEN, "pro_only");
            }
            return jobRepository.findByStatusOrderByCreatedAtDesc(JobStatus.open).stream()
                    .map(this::toJobResponse)
                    .toList();
        }
        if ("mine".equals(scope)) {
            List<Job> jobs = switch (account.getRole()) {
                case company -> jobRepository.findByCompanyAccountIdOrderByCreatedAtDesc(accountId);
                case pro -> jobRepository.findProJobs(accountId);
                case unset -> throw new ApiException(HttpStatus.FORBIDDEN, "role_required");
            };
            return jobs.stream().map(this::toJobResponse).toList();
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_scope");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getJob(UUID accountId, UUID jobId) {
        Job job = requireJob(jobId);
        requireJobAccess(accountId, job);
        Map<String, Object> body = toJobResponse(job);
        escrowPaymentRepository.findByJobId(jobId).ifPresent(escrow -> body.put("escrow", toEscrowResponse(escrow)));
        return body;
    }

    @Transactional
    public Map<String, Object> apply(UUID accountId, UUID jobId, String pitch) {
        Account account = requireRole(accountId, AccountRole.pro);
        Job job = requireJob(jobId);
        if (job.getStatus() != JobStatus.open) {
            throw new ApiException(HttpStatus.CONFLICT, "job_not_open");
        }
        if (applicationRepository.existsByJobIdAndProAccountId(jobId, account.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "already_applied");
        }
        Application application = new Application(
                UUID.randomUUID(),
                jobId,
                account.getId(),
                pitch,
                ApplicationStatus.pending,
                Instant.now()
        );
        applicationRepository.save(application);
        return Map.of(
                "applicationId", application.getId().toString(),
                "status", application.getStatus().name()
        );
    }

    @Transactional
    public Map<String, Object> acceptApplication(UUID accountId, UUID jobId, UUID applicationId) {
        Account account = requireRole(accountId, AccountRole.company);
        Job job = requireJob(jobId);
        if (!job.getCompanyAccountId().equals(account.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "not_job_owner");
        }
        if (job.getStatus() != JobStatus.open) {
            throw new ApiException(HttpStatus.CONFLICT, "job_not_open");
        }

        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "application_not_found"));
        if (!application.getJobId().equals(jobId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "application_job_mismatch");
        }
        if (application.getStatus() != ApplicationStatus.pending) {
            throw new ApiException(HttpStatus.CONFLICT, "application_not_pending");
        }

        application.setStatus(ApplicationStatus.accepted);
        applicationRepository.save(application);

        for (Application other : applicationRepository.findByJobId(jobId)) {
            if (!other.getId().equals(applicationId) && other.getStatus() == ApplicationStatus.pending) {
                other.setStatus(ApplicationStatus.cancelled);
                applicationRepository.save(other);
            }
        }

        job.setMatchedProAccountId(application.getProAccountId());
        job.setStatus(JobStatus.matched);
        job.setUpdatedAt(Instant.now());
        jobRepository.save(job);

        return toJobResponse(job);
    }

    @Transactional
    public Map<String, Object> checkout(UUID accountId, UUID jobId) {
        Account account = requireRole(accountId, AccountRole.company);
        Job job = requireJob(jobId);
        if (!job.getCompanyAccountId().equals(account.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "not_job_owner");
        }
        if (job.getStatus() != JobStatus.matched) {
            throw new ApiException(HttpStatus.CONFLICT, "job_not_matched");
        }
        return billingService.checkout(job);
    }

    @Transactional
    public Map<String, Object> startJob(UUID accountId, UUID jobId) {
        Account account = requireRole(accountId, AccountRole.pro);
        Job job = requireJob(jobId);
        if (job.getMatchedProAccountId() == null || !job.getMatchedProAccountId().equals(account.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "not_matched_pro");
        }
        if (job.getStatus() != JobStatus.funded) {
            throw new ApiException(HttpStatus.CONFLICT, "job_not_funded");
        }
        EscrowPayment escrow = escrowPaymentRepository.findByJobId(jobId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "escrow_not_found"));
        if (escrow.getStatus() != EscrowStatus.held) {
            throw new ApiException(HttpStatus.CONFLICT, "escrow_not_held");
        }

        job.setStatus(JobStatus.in_progress);
        job.setUpdatedAt(Instant.now());
        jobRepository.save(job);
        return toJobResponse(job);
    }

    @Transactional
    public Map<String, Object> completeJob(UUID accountId, UUID jobId) {
        Account account = requireRole(accountId, AccountRole.company);
        Job job = requireJob(jobId);
        if (!job.getCompanyAccountId().equals(account.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "not_job_owner");
        }
        if (job.getStatus() != JobStatus.in_progress) {
            throw new ApiException(HttpStatus.CONFLICT, "job_not_in_progress");
        }

        EscrowPayment escrow = demoEscrowService.releaseForJob(jobId);
        job.setStatus(JobStatus.completed);
        job.setUpdatedAt(Instant.now());
        jobRepository.save(job);

        Map<String, Object> body = toJobResponse(job);
        body.put("escrow", toEscrowResponse(escrow));
        return body;
    }

    @Transactional
    public Map<String, Object> cancelJob(UUID accountId, UUID jobId) {
        Account account = requireRole(accountId, AccountRole.company);
        Job job = requireJob(jobId);
        if (!job.getCompanyAccountId().equals(account.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "not_job_owner");
        }
        if (job.getStatus() != JobStatus.open && job.getStatus() != JobStatus.matched) {
            throw new ApiException(HttpStatus.CONFLICT, "job_not_cancellable");
        }
        if (escrowPaymentRepository.findByJobId(jobId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "job_already_funded");
        }

        job.setStatus(JobStatus.cancelled);
        job.setUpdatedAt(Instant.now());
        jobRepository.save(job);
        return toJobResponse(job);
    }

    private Account requireRole(UUID accountId, AccountRole expected) {
        Account account = authService.requireAccount(accountId);
        if (account.getRole() != expected) {
            throw new ApiException(HttpStatus.FORBIDDEN, "wrong_role");
        }
        return account;
    }

    private Job requireJob(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "job_not_found"));
    }

    private void requireJobAccess(UUID accountId, Job job) {
        Account account = authService.requireAccount(accountId);
        if (job.getCompanyAccountId().equals(accountId)) {
            return;
        }
        if (job.getMatchedProAccountId() != null && job.getMatchedProAccountId().equals(accountId)) {
            return;
        }
        if (account.getRole() == AccountRole.pro
                && applicationRepository.existsByJobIdAndProAccountId(job.getId(), accountId)) {
            return;
        }
        if (account.getRole() == AccountRole.pro && job.getStatus() == JobStatus.open) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "job_access_denied");
    }

    private void validateBudget(long budgetCents) {
        if (budgetCents < properties.billing().minBudgetCents()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "budget_too_low");
        }
    }

    private void validateUrgency(String urgency) {
        if (!List.of("p1", "p2", "p3").contains(urgency)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_urgency");
        }
    }

    private Map<String, Object> toJobResponse(Job job) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId", job.getId().toString());
        body.put("companyAccountId", job.getCompanyAccountId().toString());
        body.put("title", job.getTitle());
        body.put("description", job.getDescription());
        body.put("urgency", job.getUrgency());
        body.put("budgetCents", job.getBudgetCents());
        body.put("skillsNeeded", job.getSkillsNeeded());
        body.put("status", job.getStatus().name());
        if (job.getMatchedProAccountId() != null) {
            body.put("matchedProAccountId", job.getMatchedProAccountId().toString());
        }
        if (job.getResponseDeadlineAt() != null) {
            body.put("responseDeadlineAt", job.getResponseDeadlineAt().toString());
        }
        body.put("createdAt", job.getCreatedAt().toString());
        body.put("updatedAt", job.getUpdatedAt().toString());
        return body;
    }

    private Map<String, Object> toEscrowResponse(EscrowPayment escrow) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("escrowId", escrow.getId().toString());
        body.put("amountCents", escrow.getAmountCents());
        body.put("platformFeeCents", escrow.getPlatformFeeCents());
        body.put("proAmountCents", escrow.getProAmountCents());
        body.put("status", escrow.getStatus().name());
        if (escrow.getReleasedAt() != null) {
            body.put("releasedAt", escrow.getReleasedAt().toString());
        }
        return body;
    }

    public record CreateJobRequest(
            String title,
            String description,
            String urgency,
            long budgetCents,
            String skillsNeeded,
            Instant responseDeadlineAt
    ) {
    }
}
