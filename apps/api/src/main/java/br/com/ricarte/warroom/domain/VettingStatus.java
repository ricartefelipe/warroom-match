package br.com.ricarte.warroom.domain;

/** Professional verification. Only {@code approved} may apply to or be accepted for a job. */
public enum VettingStatus {
    not_submitted,
    submitted,
    approved,
    rejected,
    suspended;

    public boolean eligible() {
        return this == approved;
    }
}
