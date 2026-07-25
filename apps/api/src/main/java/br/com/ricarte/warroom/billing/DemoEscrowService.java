package br.com.ricarte.warroom.billing;

import br.com.ricarte.warroom.config.WarroomProperties;
import br.com.ricarte.warroom.domain.EscrowPayment;
import br.com.ricarte.warroom.domain.EscrowPaymentRepository;
import br.com.ricarte.warroom.domain.EscrowStatus;
import br.com.ricarte.warroom.domain.Job;
import br.com.ricarte.warroom.domain.JobRepository;
import br.com.ricarte.warroom.domain.JobStatus;
import br.com.ricarte.warroom.web.ApiException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoEscrowService {

    private final WarroomProperties properties;
    private final EscrowPaymentRepository escrowPaymentRepository;
    private final JobRepository jobRepository;

    public DemoEscrowService(
            WarroomProperties properties,
            EscrowPaymentRepository escrowPaymentRepository,
            JobRepository jobRepository
    ) {
        this.properties = properties;
        this.escrowPaymentRepository = escrowPaymentRepository;
        this.jobRepository = jobRepository;
    }

    public boolean demoMode() {
        return properties.billing().demoEscrow() || !properties.billing().stripeConfigured();
    }

    public FeeSplit computeFees(long amountCents) {
        long platformFee = amountCents * properties.billing().platformFeeBps() / 10_000L;
        long proAmount = amountCents - platformFee;
        return new FeeSplit(amountCents, platformFee, proAmount);
    }

    @Transactional
    public Map<String, Object> fundJob(Job job) {
        if (escrowPaymentRepository.findByJobId(job.getId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "escrow_already_exists");
        }
        FeeSplit fees = computeFees(job.getBudgetCents());
        Instant now = Instant.now();
        EscrowPayment escrow = new EscrowPayment(
                UUID.randomUUID(),
                job.getId(),
                fees.amountCents(),
                fees.platformFeeCents(),
                fees.proAmountCents(),
                EscrowStatus.held,
                null,
                now
        );
        escrowPaymentRepository.save(escrow);

        job.setStatus(JobStatus.funded);
        job.setUpdatedAt(now);
        jobRepository.save(job);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("demo", true);
        body.put("status", EscrowStatus.held.name());
        body.put("jobStatus", JobStatus.funded.name());
        body.put("amountCents", fees.amountCents());
        body.put("platformFeeCents", fees.platformFeeCents());
        body.put("proAmountCents", fees.proAmountCents());
        return body;
    }

    @Transactional
    public EscrowPayment releaseForJob(UUID jobId) {
        EscrowPayment escrow = escrowPaymentRepository.findByJobId(jobId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "escrow_not_found"));
        if (escrow.getStatus() != EscrowStatus.held) {
            throw new ApiException(HttpStatus.CONFLICT, "escrow_not_held");
        }
        escrow.setStatus(EscrowStatus.released);
        escrow.setReleasedAt(Instant.now());
        return escrowPaymentRepository.save(escrow);
    }

    @Transactional
    public void markHeldFromStripe(UUID jobId, String stripeSessionId) {
        EscrowPayment escrow = escrowPaymentRepository.findByJobId(jobId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "escrow_not_found"));
        escrow.setStatus(EscrowStatus.held);
        escrow.setStripeSessionId(stripeSessionId);
        escrowPaymentRepository.save(escrow);

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "job_not_found"));
        job.setStatus(JobStatus.funded);
        job.setUpdatedAt(Instant.now());
        jobRepository.save(job);
    }

    public record FeeSplit(long amountCents, long platformFeeCents, long proAmountCents) {
    }
}
