package br.com.ricarte.warroom.jobs;

import br.com.ricarte.warroom.web.AccountContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    public Map<String, Object> create(@Valid @RequestBody CreateJobRequest request) {
        return jobService.createJob(
                AccountContext.requireAccountId(),
                new JobService.CreateJobRequest(
                        request.title(),
                        request.description(),
                        request.urgency(),
                        request.budgetCents(),
                        request.skillsNeeded(),
                        request.responseDeadlineAt()
                )
        );
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam String scope) {
        return jobService.listJobs(AccountContext.requireAccountId(), scope);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable("id") UUID jobId) {
        return jobService.getJob(AccountContext.requireAccountId(), jobId);
    }

    @PostMapping("/{id}/apply")
    public Map<String, Object> apply(
            @PathVariable("id") UUID jobId,
            @Valid @RequestBody ApplyRequest request
    ) {
        return jobService.apply(AccountContext.requireAccountId(), jobId, request.pitch());
    }

    @PostMapping("/{id}/accept/{applicationId}")
    public Map<String, Object> accept(
            @PathVariable("id") UUID jobId,
            @PathVariable UUID applicationId
    ) {
        return jobService.acceptApplication(AccountContext.requireAccountId(), jobId, applicationId);
    }

    @PostMapping("/{id}/checkout")
    public Map<String, Object> checkout(@PathVariable("id") UUID jobId) {
        return jobService.checkout(AccountContext.requireAccountId(), jobId);
    }

    @PostMapping("/{id}/start")
    public Map<String, Object> start(@PathVariable("id") UUID jobId) {
        return jobService.startJob(AccountContext.requireAccountId(), jobId);
    }

    @PostMapping("/{id}/complete")
    public Map<String, Object> complete(@PathVariable("id") UUID jobId) {
        return jobService.completeJob(AccountContext.requireAccountId(), jobId);
    }

    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable("id") UUID jobId) {
        return jobService.cancelJob(AccountContext.requireAccountId(), jobId);
    }

    public record CreateJobRequest(
            @NotBlank String title,
            @NotBlank String description,
            @NotBlank String urgency,
            @Positive long budgetCents,
            String skillsNeeded,
            Instant responseDeadlineAt
    ) {
    }

    public record ApplyRequest(@NotBlank String pitch) {
    }
}
