package br.com.ricarte.warroom.account;

import br.com.ricarte.warroom.domain.ProfileRepository;
import br.com.ricarte.warroom.domain.VettingStatus;
import br.com.ricarte.warroom.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** The commercial promise "vetted professionals" enforced in one place: only approved pros work. */
@Component
public class VettingPolicy {

    private final ProfileRepository profiles;

    public VettingPolicy(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    public void requireEligible(UUID proAccountId) {
        VettingStatus status = profiles.findById(proAccountId)
                .map(profile -> profile.getVettingStatus())
                .orElse(VettingStatus.not_submitted);
        if (!status.eligible()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "pro_not_vetted");
        }
    }
}
