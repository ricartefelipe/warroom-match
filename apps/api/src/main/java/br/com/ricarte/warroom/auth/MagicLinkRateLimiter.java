package br.com.ricarte.warroom.auth;

import br.com.ricarte.warroom.config.WarroomProperties;
import br.com.ricarte.warroom.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding window per key (e-mail and client address) for magic-link requests. In-memory: enough
 * for one process; a second instance needs a shared store.
 */
@Component
public class MagicLinkRateLimiter {

    private final int emailLimit;
    private final int addressLimit;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    @Autowired
    public MagicLinkRateLimiter(WarroomProperties properties) {
        this(properties.auth().magicLinkRequestsPerWindow(), properties.auth().magicLinkRequestsPerAddress(),
                Duration.ofMinutes(properties.auth().magicLinkWindowMinutes()), Clock.systemUTC());
    }

    MagicLinkRateLimiter(int emailLimit, int addressLimit, Duration window, Clock clock) {
        this.emailLimit = emailLimit;
        this.addressLimit = addressLimit;
        this.window = window;
        this.clock = clock;
    }

    public void checkEmail(String email) {
        check("email:" + email.toLowerCase(), emailLimit);
    }

    public void checkAddress(String address) {
        check("addr:" + address, addressLimit);
    }

    private void check(String key, int limit) {
        Instant now = clock.instant();
        Deque<Instant> queue = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && queue.peekFirst().isBefore(now.minus(window))) {
                queue.pollFirst();
            }
            if (queue.size() >= limit) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "too_many_requests");
            }
            queue.addLast(now);
        }
    }
}
