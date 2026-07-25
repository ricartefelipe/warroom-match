package br.com.ricarte.warroom.reviews;

import br.com.ricarte.warroom.web.AccountContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/jobs/{jobId}/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public Map<String, Object> create(
            @PathVariable UUID jobId,
            @Valid @RequestBody ReviewRequest request
    ) {
        return reviewService.createReview(
                AccountContext.requireAccountId(),
                jobId,
                request.rating(),
                request.comment()
        );
    }

    public record ReviewRequest(
            @NotNull @Min(1) @Max(5) Integer rating,
            String comment
    ) {
    }
}
