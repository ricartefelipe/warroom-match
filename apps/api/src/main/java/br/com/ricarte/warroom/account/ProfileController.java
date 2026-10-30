package br.com.ricarte.warroom.account;

import br.com.ricarte.warroom.web.AccountContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/me")
public class ProfileController {

    private final ProfileService profileService;
    private final VettingService vettingService;

    public ProfileController(ProfileService profileService, VettingService vettingService) {
        this.profileService = profileService;
        this.vettingService = vettingService;
    }

    @PostMapping("/vetting")
    public Map<String, Object> submitVetting(@Valid @RequestBody VettingRequest request) {
        return vettingService.submit(AccountContext.requireAccountId(), request.evidence());
    }

    @GetMapping("/profile")
    public Map<String, Object> getProfile() {
        return profileService.getProfile(AccountContext.requireAccountId());
    }

    @PutMapping("/profile")
    public Map<String, Object> updateProfile(@RequestBody ProfileUpdateRequest request) {
        return profileService.updateProfile(
                AccountContext.requireAccountId(),
                new ProfileService.ProfileUpdate(
                        request.skills(),
                        request.seniority(),
                        request.availability(),
                        request.bio(),
                        request.hourlyRateCents(),
                        request.companyName()
                )
        );
    }

    @PostMapping("/role")
    public Map<String, Object> setRole(@Valid @RequestBody RoleRequest request) {
        return profileService.setRole(AccountContext.requireAccountId(), request.role());
    }

    public record ProfileUpdateRequest(
            String skills,
            String seniority,
            String availability,
            String bio,
            Long hourlyRateCents,
            String companyName
    ) {
    }

    public record RoleRequest(@NotBlank String role) {
    }

    /** Written evidence only (links, references, summary). Identity documents are never uploaded here. */
    public record VettingRequest(@NotBlank @Size(max = 8000) String evidence) {
    }
}
