package br.com.ricarte.warroom.admin;

import br.com.ricarte.warroom.account.VettingService;
import br.com.ricarte.warroom.auth.AuthService;
import br.com.ricarte.warroom.billing.PaymentService;
import br.com.ricarte.warroom.domain.Account;
import br.com.ricarte.warroom.domain.AccountRole;
import br.com.ricarte.warroom.domain.DisputeRepository;
import br.com.ricarte.warroom.domain.DisputeStatus;
import br.com.ricarte.warroom.domain.JobPayment;
import br.com.ricarte.warroom.domain.PaymentEvent;
import br.com.ricarte.warroom.domain.VettingStatus;
import br.com.ricarte.warroom.jobs.JobService;
import br.com.ricarte.warroom.jobs.JobViews;
import br.com.ricarte.warroom.web.AccountContext;
import br.com.ricarte.warroom.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Support operations. Every endpoint requires the admin role; every decision is recorded. */
@RestController
@RequestMapping("/v1/admin")
public class AdminController {

    private final AuthService authService;
    private final VettingService vettingService;
    private final JobService jobService;
    private final PaymentService paymentService;
    private final DisputeRepository disputes;
    private final JobViews views;

    public AdminController(AuthService authService, VettingService vettingService, JobService jobService,
                           PaymentService paymentService, DisputeRepository disputes, JobViews views) {
        this.authService = authService;
        this.vettingService = vettingService;
        this.jobService = jobService;
        this.paymentService = paymentService;
        this.disputes = disputes;
        this.views = views;
    }

    @GetMapping("/vetting/pending")
    public List<Map<String, Object>> pendingVetting() {
        return vettingService.pending(AccountContext.requireAccountId());
    }

    @GetMapping("/vetting/{accountId}/history")
    public List<Map<String, Object>> vettingHistory(@PathVariable UUID accountId) {
        return vettingService.history(AccountContext.requireAccountId(), accountId);
    }

    @PostMapping("/vetting/{accountId}")
    public Map<String, Object> decideVetting(@PathVariable UUID accountId, @Valid @RequestBody VettingDecision request) {
        VettingStatus decision;
        try {
            decision = VettingStatus.valueOf(request.decision());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_vetting_decision");
        }
        return vettingService.decide(AccountContext.requireAccountId(), accountId, decision, request.note());
    }

    @GetMapping("/disputes")
    public List<Map<String, Object>> openDisputes() {
        requireAdmin();
        return disputes.findByStatusOrderByCreatedAtAsc(DisputeStatus.open).stream().map(views::dispute).toList();
    }

    @PostMapping("/disputes/{jobId}/resolve")
    public Map<String, Object> resolveDispute(@PathVariable UUID jobId, @Valid @RequestBody DisputeResolution request) {
        DisputeStatus outcome = switch (request.outcome()) {
            case "release" -> DisputeStatus.resolved_release;
            case "refund" -> DisputeStatus.resolved_refund;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_dispute_outcome");
        };
        return jobService.resolveDispute(AccountContext.requireAccountId(), jobId, outcome, request.note());
    }

    @GetMapping("/payments/pending")
    public List<Map<String, Object>> pendingPayments() {
        requireAdmin();
        return paymentService.pendingRemoteOperations().stream().map(paymentService::toResponse).toList();
    }

    @GetMapping("/payments/{paymentId}/events")
    public List<Map<String, Object>> paymentEvents(@PathVariable UUID paymentId) {
        requireAdmin();
        return paymentService.history(paymentId).stream().map(AdminController::event).toList();
    }

    @PostMapping("/payments/{paymentId}/reconcile")
    public Map<String, Object> reconcile(@PathVariable UUID paymentId) {
        requireAdmin();
        JobPayment payment = paymentService.reconcile(paymentId, Instant.now());
        return paymentService.toResponse(payment);
    }

    @PostMapping("/accounts/{accountId}/suspend")
    public Map<String, Object> suspendAccount(@PathVariable UUID accountId) {
        requireAdmin();
        Account account = authService.suspend(accountId);
        return Map.of("accountId", account.getId().toString(), "enabled", account.isEnabled());
    }

    private void requireAdmin() {
        authService.requireRole(AccountContext.requireAccountId(), AccountRole.admin);
    }

    private static Map<String, Object> event(PaymentEvent event) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", event.getId().toString());
        body.put("type", event.getType());
        body.put("providerEventId", event.getProviderEventId());
        body.put("from", event.getFromStatus());
        body.put("to", event.getToStatus());
        body.put("detail", event.getDetail());
        body.put("createdAt", event.getCreatedAt().toString());
        return body;
    }

    public record VettingDecision(@NotBlank String decision, @Size(max = 4000) String note) {
    }

    public record DisputeResolution(@NotBlank String outcome, @NotBlank @Size(max = 4000) String note) {
    }
}
