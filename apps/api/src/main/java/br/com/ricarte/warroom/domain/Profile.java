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

    @Enumerated(EnumType.STRING)
    @Column(name = "vetting_status", nullable = false, length = 20)
    private VettingStatus vettingStatus = VettingStatus.not_submitted;

    @Column(name = "vetting_evidence", columnDefinition = "text")
    private String vettingEvidence;

    @Column(name = "vetting_note", columnDefinition = "text")
    private String vettingNote;

    @Column(name = "vetted_by")
    private UUID vettedBy;

    @Column(name = "vetted_at")
    private Instant vettedAt;

    @Column(name = "stripe_account_id", length = 100)
    private String stripeAccountId;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Profile() {
    }

    public Profile(UUID accountId, Instant updatedAt) {
        this.accountId = accountId;
        this.updatedAt = updatedAt;
    }

    public void submitVetting(String evidence, Instant now) {
        this.vettingEvidence = evidence;
        this.vettingStatus = VettingStatus.submitted;
        this.updatedAt = now;
    }

    public void decideVetting(VettingStatus decision, UUID actorAccountId, String note, Instant now) {
        this.vettingStatus = decision;
        this.vettedBy = actorAccountId;
        this.vettedAt = now;
        this.vettingNote = note;
        this.updatedAt = now;
    }

    public boolean eligibleForJobs() {
        return vettingStatus.eligible();
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

    public VettingStatus getVettingStatus() {
        return vettingStatus;
    }

    public String getVettingEvidence() {
        return vettingEvidence;
    }

    public String getVettingNote() {
        return vettingNote;
    }

    public UUID getVettedBy() {
        return vettedBy;
    }

    public Instant getVettedAt() {
        return vettedAt;
    }

    public String getStripeAccountId() {
        return stripeAccountId;
    }

    public void setStripeAccountId(String stripeAccountId) {
        this.stripeAccountId = stripeAccountId;
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
