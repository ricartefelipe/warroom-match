package br.com.ricarte.warroom.auth;

import br.com.ricarte.warroom.web.AccountContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SessionAuthFilter extends OncePerRequestFilter {

    private final AuthService authService;

    public SessionAuthFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String path = request.getRequestURI();
            if (requiresSession(path)) {
                Optional<UUID> accountId = resolveSession(request);
                if (accountId.isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"unauthorized\"}");
                    return;
                }
                AccountContext.set(accountId.get());
            }
            filterChain.doFilter(request, response);
        } finally {
            AccountContext.clear();
        }
    }

    private Optional<UUID> resolveSession(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return authService.resolveAccountId(header.substring("Bearer ".length()).trim());
        }
        return Optional.empty();
    }

    private boolean requiresSession(String path) {
        if (path.startsWith("/v1/auth/magic-link") || path.startsWith("/v1/auth/verify")) {
            return false;
        }
        if (path.startsWith("/v1/billing/stripe/webhook")) {
            return false;
        }
        if (path.startsWith("/actuator")) {
            return false;
        }
        return path.startsWith("/v1/");
    }
}
