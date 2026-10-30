package br.com.ricarte.warroom.auth;

import br.com.ricarte.warroom.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MagicLinkRateLimiterTest {

    private static final Instant START = Instant.parse("2026-10-30T10:00:00Z");

    @Test
    void sixthRequestForTheSameEmailInTheWindowIsRefusedWith429() {
        MagicLinkRateLimiter limiter = new MagicLinkRateLimiter(5, 100, Duration.ofMinutes(15), Clock.fixed(START, ZoneOffset.UTC));
        for (int i = 0; i < 5; i++) {
            limiter.checkEmail("a@example.com");
        }
        assertThatThrownBy(() -> limiter.checkEmail("A@example.com"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThatCode(() -> limiter.checkEmail("b@example.com")).doesNotThrowAnyException();
    }

    @Test
    void requestsOutsideTheWindowAreForgotten() {
        MutableClock clock = new MutableClock(START);
        MagicLinkRateLimiter limiter = new MagicLinkRateLimiter(1, 100, Duration.ofMinutes(15), clock);
        limiter.checkEmail("a@example.com");
        assertThatThrownBy(() -> limiter.checkEmail("a@example.com")).isInstanceOf(ApiException.class);
        clock.advance(Duration.ofMinutes(16));
        assertThatCode(() -> limiter.checkEmail("a@example.com")).doesNotThrowAnyException();
    }

    @Test
    void addressLimitIsIndependentFromTheEmailLimit() {
        MagicLinkRateLimiter limiter = new MagicLinkRateLimiter(1, 2, Duration.ofMinutes(15), Clock.fixed(START, ZoneOffset.UTC));
        limiter.checkAddress("10.0.0.1");
        limiter.checkAddress("10.0.0.1");
        assertThatThrownBy(() -> limiter.checkAddress("10.0.0.1")).isInstanceOf(ApiException.class);
        assertThatCode(() -> limiter.checkEmail("fresh@example.com")).doesNotThrowAnyException();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
