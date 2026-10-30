package br.com.ricarte.warroom.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of a job. Every change goes through {@link #canTransitionTo(JobStatus)}; a job in a
 * terminal state never moves again. Money never moves because of a job transition alone: the
 * payment has its own machine ({@link PaymentStatus}).
 */
public enum JobStatus {
    draft,
    open,
    matched,
    funded,
    in_progress,
    delivered,
    completed,
    disputed,
    cancelled;

    public Set<JobStatus> next() {
        return switch (this) {
            case draft -> EnumSet.of(open, cancelled);
            case open -> EnumSet.of(matched, cancelled);
            case matched -> EnumSet.of(funded, cancelled);
            case funded -> EnumSet.of(in_progress, cancelled, disputed);
            case in_progress -> EnumSet.of(delivered, completed, disputed);
            case delivered -> EnumSet.of(completed, disputed);
            case disputed -> EnumSet.of(completed, cancelled);
            case completed, cancelled -> EnumSet.noneOf(JobStatus.class);
        };
    }

    public boolean canTransitionTo(JobStatus target) {
        return next().contains(target);
    }

    public boolean terminal() {
        return next().isEmpty();
    }
}
