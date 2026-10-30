package br.com.ricarte.warroom.messages;

import br.com.ricarte.warroom.auth.AuthService;
import br.com.ricarte.warroom.domain.Account;
import br.com.ricarte.warroom.domain.AccountRepository;
import br.com.ricarte.warroom.domain.AccountRole;
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

    static final int MAX_LENGTH = 4000;

    private final AuthService authService;
    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final MessageRepository messageRepository;
    private final AccountRepository accountRepository;

    public MessageService(
            AuthService authService,
            JobRepository jobRepository,
            ApplicationRepository applicationRepository,
            MessageRepository messageRepository,
            AccountRepository accountRepository
    ) {
        this.accountRepository = accountRepository;
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
        Account sender = requireParticipant(accountId, job);
        if (sender.getRole() == AccountRole.admin) {
            throw new ApiException(HttpStatus.FORBIDDEN, "support_reads_only");
        }
        Message message = new Message(
                UUID.randomUUID(),
                jobId,
                accountId,
                sanitize(body),
                Instant.now()
        );
        messageRepository.save(message);
        return toResponse(message);
    }

    private Job requireJob(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "job_not_found"));
    }

    /**
     * The chat opens after the match and only between the company, the matched professional and
     * support (read-only). Other applicants never see it.
     */
    private Account requireParticipant(UUID accountId, Job job) {
        Account account = authService.requireAccount(accountId);
        if (job.getMatchedProAccountId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "chat_not_open");
        }
        if (account.getRole() == AccountRole.admin || job.isOwner(accountId) || job.isMatchedPro(accountId)) {
            return account;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "job_access_denied");
    }

    static String sanitize(String body) {
        String cleaned = body.chars()
                .filter(c -> !Character.isISOControl(c) || c == '\n' || c == '\t')
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString()
                .strip();
        if (cleaned.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "message_empty");
        }
        if (cleaned.length() > MAX_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "message_too_long");
        }
        return cleaned;
    }

    private Map<String, Object> toResponse(Message message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", message.getId().toString());
        body.put("senderName", accountRepository.findById(message.getSenderAccountId()).map(Account::getName).orElse(null));
        body.put("jobId", message.getJobId().toString());
        body.put("senderAccountId", message.getSenderAccountId().toString());
        body.put("body", message.getBody());
        body.put("createdAt", message.getCreatedAt().toString());
        return body;
    }
}
