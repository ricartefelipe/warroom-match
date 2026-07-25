package br.com.ricarte.warroom.account;

import br.com.ricarte.warroom.auth.AuthService;
import br.com.ricarte.warroom.domain.Account;
import br.com.ricarte.warroom.domain.AccountRole;
import br.com.ricarte.warroom.domain.Availability;
import br.com.ricarte.warroom.domain.Profile;
import br.com.ricarte.warroom.domain.ProfileRepository;
import br.com.ricarte.warroom.web.AccountContext;
import br.com.ricarte.warroom.web.ApiException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final AuthService authService;
    private final ProfileRepository profileRepository;

    public ProfileService(AuthService authService, ProfileRepository profileRepository) {
        this.authService = authService;
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getProfile(UUID accountId) {
        Account account = authService.requireAccount(accountId);
        Profile profile = profileRepository.findById(accountId).orElse(null);
        return toResponse(account, profile);
    }

    @Transactional
    public Map<String, Object> updateProfile(UUID accountId, ProfileUpdate update) {
        Account account = authService.requireAccount(accountId);
        Instant now = Instant.now();
        Profile profile = profileRepository.findById(accountId).orElseGet(() -> new Profile(accountId, now));

        if (update.skills() != null) {
            profile.setSkills(update.skills());
        }
        if (update.seniority() != null) {
            profile.setSeniority(update.seniority());
        }
        if (update.availability() != null) {
            profile.setAvailability(parseAvailability(update.availability()));
        }
        if (update.bio() != null) {
            profile.setBio(update.bio());
        }
        if (update.hourlyRateCents() != null) {
            profile.setHourlyRateCents(update.hourlyRateCents());
        }
        if (update.companyName() != null) {
            profile.setCompanyName(update.companyName());
        }
        profile.setUpdatedAt(now);
        profileRepository.save(profile);
        return toResponse(account, profile);
    }

    @Transactional
    public Map<String, Object> setRole(UUID accountId, String roleRaw) {
        AccountRole role = parseRole(roleRaw);
        Account account = authService.setRole(accountId, role);
        Instant now = Instant.now();
        profileRepository.findById(accountId).orElseGet(() -> {
            Profile profile = new Profile(accountId, now);
            return profileRepository.save(profile);
        });
        return Map.of(
                "accountId", account.getId().toString(),
                "role", account.getRole().name()
        );
    }

    private Map<String, Object> toResponse(Account account, Profile profile) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountId", account.getId().toString());
        body.put("email", account.getEmail());
        body.put("name", account.getName());
        body.put("role", account.getRole().name());
        if (profile != null) {
            body.put("skills", profile.getSkills());
            body.put("seniority", profile.getSeniority());
            body.put("availability", profile.getAvailability() == null ? null : profile.getAvailability().name());
            body.put("bio", profile.getBio());
            body.put("hourlyRateCents", profile.getHourlyRateCents());
            body.put("vetted", profile.isVetted());
            body.put("companyName", profile.getCompanyName());
            body.put("updatedAt", profile.getUpdatedAt().toString());
        }
        return body;
    }

    private AccountRole parseRole(String roleRaw) {
        try {
            return AccountRole.valueOf(roleRaw);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_role");
        }
    }

    private Availability parseAvailability(String value) {
        try {
            return Availability.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_availability");
        }
    }

    public record ProfileUpdate(
            String skills,
            String seniority,
            String availability,
            String bio,
            Long hourlyRateCents,
            String companyName
    ) {
    }
}
