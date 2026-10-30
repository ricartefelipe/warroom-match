package br.com.ricarte.warroom.jobs;

import br.com.ricarte.warroom.account.VettingPolicy;
import br.com.ricarte.warroom.auth.AuthService;
import br.com.ricarte.warroom.billing.PaymentService;
import br.com.ricarte.warroom.config.WarroomProperties;
import br.com.ricarte.warroom.domain.Account;
import br.com.ricarte.warroom.domain.AccountRole;
import br.com.ricarte.warroom.domain.Application;
import br.com.ricarte.warroom.domain.ApplicationRepository;
import br.com.ricarte.warroom.domain.ApplicationStatus;
import br.com.ricarte.warroom.domain.Dispute;
import br.com.ricarte.warroom.domain.DisputeRepository;
import br.com.ricarte.warroom.domain.DisputeStatus;
import br.com.ricarte.warroom.domain.Job;
import br.com.ricarte.warroom.domain.JobPayment;
import br.com.ricarte.warroom.domain.JobRepository;
import br.com.ricarte.warroom.domain.JobStatus;
import br.com.ricarte.warroom.domain.PaymentStatus;
import br.com.ricarte.warroom.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Job lifecycle. Every state change goes through {@link Job#transitionTo}; concurrent changes are
 * serialized by the optimistic lock on {@link Job}. Money is delegated to {@link PaymentService}
 * and a job never claims a financial state the provider did not confirm.
 */
@Service
public class JobService {

    static final List<String> URGENCIES = List.of("p1", "p2", "p3");

    private final AuthService authService;
    private final WarroomProperties properties;
    private final JobRepository jobs;
    private final ApplicationRepository applications;
    private final DisputeRepository disputes;
    private final PaymentService paymentService;
    private final VettingPolicy vettingPolicy;
    private final JobViews views;

    public JobService(AuthService authService, WarroomProperties properties, JobRepository jobs,
                      ApplicationRepository applications, DisputeRepository disputes, PaymentService paymentService,
                      VettingPolicy vettingPolicy, JobViews views) {
        this.authService = authService;
        this.properties = properties;
        this.jobs = jobs;
        this.applications = applications;
        this.disputes = disputes;
        this.paymentService = paymentService;
        this.vettingPolicy = vettingPolicy;
        this.views = views;
    }

    @Transactional
    public Map<String, Object> createJob(UUID accountId, CreateJobRequest request) {
        Account account = authService.requireRole(accountId, AccountRole.company);
        if (request.budgetCents() < properties.billing().minBudgetCents()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "budget_too_low");
        }
        paymentService.fees(request.budgetCents());
        if (!URGENCIES.contains(request.urgency())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_urgency");
        }
        Instant now = Instant.now();
        if (request.responseDeadlineAt() != null && !request.responseDeadlineAt().isAfter(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "deadline_in_past");
        }
        Job job = new Job(UUID.randomUUID(), account.getId(), request.title(), request.description(),
                request.urgency(), request.budgetCents(), request.skillsNeeded(), JobStatus.open,
                request.responseDeadlineAt(), now);
        jobs.save(job);
        return views.detail(job, viewer(account));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listJobs(UUID accountId, String scope) {
        Account account = authService.requireAccount(accountId);
        List<Job> result = switch (scope) {
            case "open" -> {
                if (account.getRole() != AccountRole.pro) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "pro_only");
                }
                yield jobs.findByStatusOrderByCreatedAtDesc(JobStatus.open);
            }
            case "mine" -> switch (account.getRole()) {
                case company -> jobs.findByCompanyAccountIdOrderByCreatedAtDesc(accountId);
                case pro -> jobs.findProJobs(accountId);
                case admin -> jobs.findAllByOrderByCreatedAtDesc();
                case unset -> throw new ApiException(HttpStatus.FORBIDDEN, "role_required");
            };
            case "all" -> {
                authService.requireRole(accountId, AccountRole.admin);
                yield jobs.findAllByOrderByCreatedAtDesc();
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_scope");
        };
        JobViews.Viewer viewer = viewer(account);
        return result.stream().map(job -> views.summary(job, viewer)).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getJob(UUID accountId, UUID jobId) {
        Job job = requireJob(jobId);
        Account account = requireJobAccess(accountId, job);
        return views.detail(job, viewer(account));
    }

    @Transactional
    public Map<String, Object> apply(UUID accountId, UUID jobId, String pitch) {
        Account account = authService.requireRole(accountId, AccountRole.pro);
        vettingPolicy.requireEligible(accountId);
        Job job = requireJob(jobId);
        Instant now = Instant.now();
        if (job.getStatus() != JobStatus.open) {
            throw new ApiException(HttpStatus.CONFLICT, "job_not_open");
        }
        if (job.deadlinePassed(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "deadline_passed");
        }
        if (applications.existsByJobIdAndProAccountId(jobId, account.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "already_applied");
        }
        Application application = new Application(UUID.randomUUID(), jobId, account.getId(), pitch,
                ApplicationStatus.pending, now);
        applications.save(application);
        return views.application(application);
    }

    @Transactional
    public Map<String, Object> acceptApplication(UUID accountId, UUID jobId, UUID applicationId) {
        Account account = authService.requireRole(accountId, AccountRole.company);
        Job job = requireOwnedJob(account, jobId);
        Instant now = Instant.now();
        if (job.deadlinePassed(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "deadline_passed");
        }
        Application application = applications.findById(applicationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "application_not_found"));
        if (!application.getJobId().equals(jobId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "application_job_mismatch");
        }
        if (application.getStatus() != ApplicationStatus.pending) {
            throw new ApiException(HttpStatus.CONFLICT, "application_not_pending");
        }
        vettingPolicy.requireEligible(application.getProAccountId());
        job.match(application.getProAccountId(), now);
        jobs.saveAndFlush(job);
        application.setStatus(ApplicationStatus.accepted);
        applications.saveAndFlush(application);
        for (Application other : applications.findByJobId(jobId)) {
            if (!other.getId().equals(applicationId) && other.getStatus() == ApplicationStatus.pending) {
                other.setStatus(ApplicationStatus.cancelled);
                applications.save(other);
            }
        }
        return views.detail(job, viewer(account));
    }

    @Transactional
    public Map<String, Object> checkout(UUID accountId, UUID jobId) {
        Account account = authService.requireRole(accountId, AccountRole.company);
        Job job = requireOwnedJob(account, jobId);
        if (job.getStatus() != JobStatus.matched) {
            throw new ApiException(HttpStatus.CONFLICT, "job_not_matched");
        }
        PaymentService.CheckoutResult result = paymentService.checkout(job);
        if (result.paidImmediately()) {
            job.transitionTo(JobStatus.funded, Instant.now());
            jobs.save(job);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("demo", paymentService.demo());
        body.put("url", result.url());
        body.put("paymentId", result.payment().getId().toString());
        body.put("status", result.payment().getStatus().name());
        body.put("jobStatus", job.getStatus().name());
        body.put("payment", paymentService.toResponse(result.payment()));
        return body;
    }

    /** Called by the provider webhook after a verified payment. */
    @Transactional
    public void onPaymentConfirmed(UUID jobId, Instant now) {
        Job job = requireJob(jobId);
        if (job.getStatus().canTransitionTo(JobStatus.funded)) {
            job.transitionTo(JobStatus.funded, now);
            jobs.save(job);
        }
    }

    /** Called by the provider webhook when the payer disputed the charge. */
    @Transactional
    public void onProviderDispute(UUID jobId, Instant now) {
        Job job = requireJob(jobId);
        if (job.getStatus().canTransitionTo(JobStatus.disputed)) {
            job.transitionTo(JobStatus.disputed, now);
            jobs.save(job);
            disputes.findByJobIdAndStatus(jobId, DisputeStatus.open).orElseGet(() -> disputes.save(
                    new Dispute(UUID.randomUUID(), jobId, job.getCompanyAccountId(), "provider chargeback", now)));
        }
    }

    @Transactional
    public Map<String, Object> startJob(UUID accountId, UUID jobId) {
        Account account = authService.requireRole(accountId, AccountRole.pro);
        Job job = requireJob(jobId);
        if (!job.isMatchedPro(account.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "not_matched_pro");
        }
        requirePaymentStatus(job, PaymentStatus.paid, "payment_not_confirmed");
        job.transitionTo(JobStatus.in_progress, Instant.now());
        jobs.save(job);
        return views.detail(job, viewer(account));
    }

    @Transactional
    public Map<String, Object> deliverJob(UUID accountId, UUID jobId) {
        Account account = authService.requireRole(accountId, AccountRole.pro);
        Job job = requireJob(jobId);
        if (!job.isMatchedPro(account.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "not_matched_pro");
        }
        job.transitionTo(JobStatus.delivered, Instant.now());
        jobs.save(job);
        return views.detail(job, viewer(account));
    }

    /**
     * The company accepts the work. The job is completed and the payout is requested; the payment
     * only becomes {@code released} when the provider confirms the transfer.
     */
    @Transactional
    public Map<String, Object> completeJob(UUID accountId, UUID jobId) {
        Account account = authService.requireRole(accountId, AccountRole.company);
        Job job = requireOwnedJob(account, jobId);
        if (job.getStatus() == JobStatus.disputed) {
            throw new ApiException(HttpStatus.CONFLICT, "job_disputed");
        }
        Instant now = Instant.now();
        job.transitionTo(JobStatus.completed, now);
        jobs.save(job);
        paymentService.requestPayout(job, now);
        return views.detail(job, viewer(account));
    }

    @Transactional
    public Map<String, Object> cancelJob(UUID accountId, UUID jobId) {
        Account account = authService.requireRole(accountId, AccountRole.company);
        Job job = requireOwnedJob(account, jobId);
        Instant now = Instant.now();
        job.transitionTo(JobStatus.cancelled, now);
        jobs.save(job);
        paymentService.findByJob(jobId)
                .filter(payment -> payment.getStatus().fundsHeld())
                .ifPresent(payment -> paymentService.requestRefund(job, now));
        return views.detail(job, viewer(account));
    }

    @Transactional
    public Map<String, Object> openDispute(UUID accountId, UUID jobId, String reason) {
        Account account = authService.requireAccount(accountId);
        Job job = requireJob(jobId);
        if (!job.isOwner(accountId) && !job.isMatchedPro(accountId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "job_access_denied");
        }
        Instant now = Instant.now();
        job.transitionTo(JobStatus.disputed, now);
        jobs.save(job);
        paymentService.openDispute(job, now);
        Dispute dispute = disputes.save(new Dispute(UUID.randomUUID(), jobId, accountId, reason, now));
        Map<String, Object> body = views.detail(job, viewer(account));
        body.put("dispute", views.dispute(dispute));
        return body;
    }

    /** Support decides: release pays the professional, refund returns the money to the company. */
    @Transactional
    public Map<String, Object> resolveDispute(UUID adminAccountId, UUID jobId, DisputeStatus outcome, String note) {
        Account admin = authService.requireRole(adminAccountId, AccountRole.admin);
        Job job = requireJob(jobId);
        Dispute dispute = disputes.findByJobIdAndStatus(jobId, DisputeStatus.open)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "dispute_not_found"));
        Instant now = Instant.now();
        dispute.resolve(outcome, adminAccountId, note, now);
        disputes.save(dispute);
        if (outcome == DisputeStatus.resolved_release) {
            job.transitionTo(JobStatus.completed, now);
            jobs.save(job);
            paymentService.requestPayout(job, now);
        } else {
            job.transitionTo(JobStatus.cancelled, now);
            jobs.save(job);
            paymentService.requestRefund(job, now);
        }
        Map<String, Object> body = views.detail(job, viewer(admin));
        body.put("dispute", views.dispute(dispute));
        return body;
    }

    private void requirePaymentStatus(Job job, PaymentStatus expected, String code) {
        JobPayment payment = paymentService.findByJob(job.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "payment_not_found"));
        if (payment.getStatus() != expected) {
            throw new ApiException(HttpStatus.CONFLICT, code);
        }
    }

    private Job requireOwnedJob(Account company, UUID jobId) {
        Job job = requireJob(jobId);
        if (!job.isOwner(company.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "not_job_owner");
        }
        return job;
    }

    private Job requireJob(UUID jobId) {
        return jobs.findById(jobId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "job_not_found"));
    }

    private Account requireJobAccess(UUID accountId, Job job) {
        Account account = authService.requireAccount(accountId);
        boolean allowed = account.getRole() == AccountRole.admin
                || job.isOwner(accountId)
                || job.isMatchedPro(accountId)
                || (account.getRole() == AccountRole.pro
                    && (job.getStatus() == JobStatus.open || applications.existsByJobIdAndProAccountId(job.getId(), accountId)));
        if (!allowed) {
            throw new ApiException(HttpStatus.FORBIDDEN, "job_access_denied");
        }
        return account;
    }

    private static JobViews.Viewer viewer(Account account) {
        return new JobViews.Viewer(account.getId(), account.getRole() == AccountRole.pro, account.getRole() == AccountRole.admin);
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
