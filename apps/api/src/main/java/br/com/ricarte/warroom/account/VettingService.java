package br.com.ricarte.warroom.account;

import br.com.ricarte.warroom.auth.AuthService;
import br.com.ricarte.warroom.domain.Account;
import br.com.ricarte.warroom.domain.AccountRole;
import br.com.ricarte.warroom.domain.Profile;
import br.com.ricarte.warroom.domain.ProfileRepository;
import br.com.ricarte.warroom.domain.VettingEvent;
import br.com.ricarte.warroom.domain.VettingEventRepository;
import br.com.ricarte.warroom.domain.VettingStatus;
import br.com.ricarte.warroom.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Professional verification: the pro submits written evidence (links, references, a summary; no
 * identity documents), support approves, rejects or suspends, and every decision is audited.
 */
@Service
public class VettingService {

    private final AuthService authService;
    private final ProfileRepository profiles;
    private final VettingEventRepository events;

    public VettingService(AuthService authService, ProfileRepository profiles, VettingEventRepository events) {
        this.authService = authService;
        this.profiles = profiles;
        this.events = events;
    }

    @Transactional
    public Map<String, Object> submit(UUID accountId, String evidence) {
        authService.requireRole(accountId, AccountRole.pro);
        Instant now = Instant.now();
        Profile profile = profiles.findById(accountId).orElseGet(() -> new Profile(accountId, now));
        VettingStatus from = profile.getVettingStatus();
        if (from == VettingStatus.approved || from == VettingStatus.suspended) {
            throw new ApiException(HttpStatus.CONFLICT, "vetting_not_resubmittable");
        }
        profile.submitVetting(evidence, now);
        profiles.save(profile);
        events.save(new VettingEvent(UUID.randomUUID(), accountId, from, VettingStatus.submitted, accountId, null, now));
        return status(profile);
    }

    @Transactional
    public Map<String, Object> decide(UUID adminAccountId, UUID proAccountId, VettingStatus decision, String note) {
        authService.requireRole(adminAccountId, AccountRole.admin);
        if (decision != VettingStatus.approved && decision != VettingStatus.rejected && decision != VettingStatus.suspended) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_vetting_decision");
        }
        Account pro = authService.requireAccount(proAccountId);
        if (pro.getRole() != AccountRole.pro) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "account_not_pro");
        }
        Instant now = Instant.now();
        Profile profile = profiles.findById(proAccountId).orElseGet(() -> new Profile(proAccountId, now));
        VettingStatus from = profile.getVettingStatus();
        profile.decideVetting(decision, adminAccountId, note, now);
        profiles.save(profile);
        events.save(new VettingEvent(UUID.randomUUID(), proAccountId, from, decision, adminAccountId, note, now));
        return status(profile);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> pending(UUID adminAccountId) {
        authService.requireRole(adminAccountId, AccountRole.admin);
        return profiles.findByVettingStatusOrderByUpdatedAtAsc(VettingStatus.submitted).stream()
                .map(this::status)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(UUID adminAccountId, UUID proAccountId) {
        authService.requireRole(adminAccountId, AccountRole.admin);
        return events.findByAccountIdOrderByCreatedAtAsc(proAccountId).stream().map(event -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", event.getId().toString());
            body.put("from", event.getFromStatus().name());
            body.put("to", event.getToStatus().name());
            body.put("actorAccountId", event.getActorAccountId().toString());
            body.put("note", event.getNote());
            body.put("createdAt", event.getCreatedAt().toString());
            return body;
        }).toList();
    }

    public Map<String, Object> status(Profile profile) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountId", profile.getAccountId().toString());
        body.put("vettingStatus", profile.getVettingStatus().name());
        body.put("vettingEvidence", profile.getVettingEvidence());
        body.put("vettingNote", profile.getVettingNote());
        body.put("vettedAt", profile.getVettedAt() == null ? null : profile.getVettedAt().toString());
        body.put("payoutAccountConfigured", profile.getStripeAccountId() != null && !profile.getStripeAccountId().isBlank());
        return body;
    }

    private void requireAdmin(UUID accountId) {
        Account account = authService.requireAccount(accountId);
        if (account.getRole() != AccountRole.admin) {
            throw new ApiException(HttpStatus.FORBIDDEN, "admin_only");
        }
    }
}
