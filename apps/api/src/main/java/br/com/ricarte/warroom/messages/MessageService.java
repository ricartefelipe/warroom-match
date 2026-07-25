package br.com.ricarte.warroom.messages;

import br.com.ricarte.warroom.auth.AuthService;
import br.com.ricarte.warroom.domain.ApplicationRepository;
import br.com.ricarte.warroom.domain.Job;
import br.com.ricarte.warroom.domain.JobRepository;
import br.com.ricarte.warroom.domain.Message;
import br.com.ricarte.warroom.domain.MessageRepository;
import br.com.ricarte.warroom.web.ApiException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageService {

    private final AuthService authService;
    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final MessageRepository messageRepository;

    public MessageService(
            AuthService authService,
            JobRepository jobRepository,
            ApplicationRepository applicationRepository,
            MessageRepository messageRepository
    ) {
        this.authService = authService;
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listMessages(UUID accountId, UUID jobId) {
        Job job = requireJob(jobId);
        requireParticipant(accountId, job);
        return messageRepository.findByJobIdOrderByCreatedAtAsc(jobId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public Map<String, Object> postMessage(UUID accountId, UUID jobId, String body) {
        Job job = requireJob(jobId);
        requireParticipant(accountId, job);
        Message message = new Message(
                UUID.randomUUID(),
                jobId,
                accountId,
                body,
                Instant.now()
        );
        messageRepository.save(message);
        return toResponse(message);
    }

    private Job requireJob(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "job_not_found"));
    }

    private void requireParticipant(UUID accountId, Job job) {
        authService.requireAccount(accountId);
        if (job.getCompanyAccountId().equals(accountId)) {
            return;
        }
        if (job.getMatchedProAccountId() != null && job.getMatchedProAccountId().equals(accountId)) {
            return;
        }
        if (applicationRepository.existsByJobIdAndProAccountId(job.getId(), accountId)) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "job_access_denied");
    }

    private Map<String, Object> toResponse(Message message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messageId", message.getId().toString());
        body.put("jobId", message.getJobId().toString());
        body.put("senderAccountId", message.getSenderAccountId().toString());
        body.put("body", message.getBody());
        body.put("createdAt", message.getCreatedAt().toString());
        return body;
    }
}
