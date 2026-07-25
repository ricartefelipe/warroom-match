package br.com.ricarte.warroom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "profiles")
public class Profile {

    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @Column(columnDefinition = "text")
    private String skills;

    @Column(length = 40)
    private String seniority;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Availability availability;

    @Column(columnDefinition = "text")
    private String bio;

    @Column(name = "hourly_rate_cents")
    private Long hourlyRateCents;

    @Column(nullable = false)
    private boolean vetted;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Profile() {
    }

    public Profile(UUID accountId, Instant updatedAt) {
        this.accountId = accountId;
        this.vetted = false;
        this.updatedAt = updatedAt;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getSkills() {
        return skills;
    }

    public void setSkills(String skills) {
        this.skills = skills;
    }

    public String getSeniority() {
        return seniority;
    }

    public void setSeniority(String seniority) {
        this.seniority = seniority;
    }

    public Availability getAvailability() {
        return availability;
    }

    public void setAvailability(Availability availability) {
        this.availability = availability;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public Long getHourlyRateCents() {
        return hourlyRateCents;
    }

    public void setHourlyRateCents(Long hourlyRateCents) {
        this.hourlyRateCents = hourlyRateCents;
    }

    public boolean isVetted() {
        return vetted;
    }

    public void setVetted(boolean vetted) {
        this.vetted = vetted;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
