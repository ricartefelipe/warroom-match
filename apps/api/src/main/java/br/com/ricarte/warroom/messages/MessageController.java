package br.com.ricarte.warroom.messages;

import br.com.ricarte.warroom.web.AccountContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/jobs/{jobId}/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public List<Map<String, Object>> list(@PathVariable UUID jobId) {
        return messageService.listMessages(AccountContext.requireAccountId(), jobId);
    }

    @PostMapping
    public Map<String, Object> post(
            @PathVariable UUID jobId,
            @Valid @RequestBody MessageRequest request
    ) {
        return messageService.postMessage(AccountContext.requireAccountId(), jobId, request.body());
    }

    public record MessageRequest(@NotBlank String body) {
    }
}
