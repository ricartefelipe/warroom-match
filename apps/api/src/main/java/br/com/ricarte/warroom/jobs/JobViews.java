package br.com.ricarte.warroom.jobs;

import br.com.ricarte.warroom.billing.PaymentService;
import br.com.ricarte.warroom.domain.Account;
import br.com.ricarte.warroom.domain.AccountRepository;
import br.com.ricarte.warroom.domain.Application;
import br.com.ricarte.warroom.domain.ApplicationRepository;
import br.com.ricarte.warroom.domain.Dispute;
import br.com.ricarte.warroom.domain.DisputeRepository;
import br.com.ricarte.warroom.domain.DisputeStatus;
import br.com.ricarte.warroom.domain.Job;
import br.com.ricarte.warroom.domain.Profile;
import br.com.ricarte.warroom.domain.ProfileRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Builds the job representation the panel consumes. What each viewer sees depends on its role. */
@Component
public class JobViews {

    private final AccountRepository accounts;
    private final ProfileRepository profiles;
    private final ApplicationRepository applications;
    private final DisputeRepository disputes;
    private final PaymentService paymentService;

    public JobViews(AccountRepository accounts, ProfileRepository profiles, ApplicationRepository applications,
                    DisputeRepository disputes, PaymentService paymentService) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.applications = applications;
        this.disputes = disputes;
        this.paymentService = paymentService;
    }

    public Map<String, Object> summary(Job job, Viewer viewer) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", job.getId().toString());
        body.put("title", job.getTitle());
        body.put("description", job.getDescription());
        body.put("urgency", job.getUrgency());
        body.put("budgetCents", job.getBudgetCents());
        body.put("skillsNeeded", skills(job.getSkillsNeeded()));
        body.put("status", job.getStatus().name());
        body.put("companyAccountId", job.getCompanyAccountId().toString());
        body.put("companyName", companyName(job.getCompanyAccountId()));
        body.put("matchedProAccountId", stringOrNull(job.getMatchedProAccountId()));
        body.put("matchedProName", job.getMatchedProAccountId() == null ? null : accountName(job.getMatchedProAccountId()));
        body.put("responseDeadlineAt", stamp(job.getResponseDeadlineAt()));
        body.put("deliveredAt", stamp(job.getDeliveredAt()));
        body.put("createdAt", job.getCreatedAt().toString());
        body.put("updatedAt", job.getUpdatedAt().toString());
        if (viewer.pro()) {
            body.put("myApplicationStatus", applications.findByJobIdAndProAccountId(job.getId(), viewer.accountId())
                    .map(application -> application.getStatus().name()).orElse(null));
        }
        return body;
    }

    public Map<String, Object> detail(Job job, Viewer viewer) {
        Map<String, Object> body = summary(job, viewer);
        if (viewer.canSeeApplications(job)) {
            body.put("applications", applications.findByJobId(job.getId()).stream().map(this::application).toList());
        }
        paymentService.findByJob(job.getId()).ifPresent(payment -> body.put("payment", paymentService.toResponse(payment)));
        disputes.findByJobIdAndStatus(job.getId(), DisputeStatus.open).ifPresent(dispute -> body.put("dispute", dispute(dispute)));
        return body;
    }

    public Map<String, Object> application(Application application) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", application.getId().toString());
        body.put("jobId", application.getJobId().toString());
        body.put("proAccountId", application.getProAccountId().toString());
        body.put("proName", accountName(application.getProAccountId()));
        body.put("pitch", application.getPitch());
        body.put("status", application.getStatus().name());
        body.put("createdAt", application.getCreatedAt().toString());
        return body;
    }

    public Map<String, Object> dispute(Dispute dispute) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", dispute.getId().toString());
        body.put("jobId", dispute.getJobId().toString());
        body.put("openedByAccountId", dispute.getOpenedByAccountId().toString());
        body.put("reason", dispute.getReason());
        body.put("status", dispute.getStatus().name());
        body.put("resolvedByAccountId", stringOrNull(dispute.getResolvedByAccountId()));
        body.put("resolutionNote", dispute.getResolutionNote());
        body.put("createdAt", dispute.getCreatedAt().toString());
        body.put("resolvedAt", stamp(dispute.getResolvedAt()));
        return body;
    }

    private String companyName(UUID companyAccountId) {
        Optional<String> fromProfile = profiles.findById(companyAccountId).map(Profile::getCompanyName)
                .filter(name -> name != null && !name.isBlank());
        return fromProfile.orElseGet(() -> accountName(companyAccountId));
    }

    private String accountName(UUID accountId) {
        return accounts.findById(accountId).map(Account::getName).orElse(null);
    }

    private static List<String> skills(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String stringOrNull(UUID value) {
        return value == null ? null : value.toString();
    }

    private static String stamp(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    /** Who is looking: drives what is included. */
    public record Viewer(UUID accountId, boolean pro, boolean admin) {

        boolean canSeeApplications(Job job) {
            return admin || job.isOwner(accountId);
        }
    }
}
