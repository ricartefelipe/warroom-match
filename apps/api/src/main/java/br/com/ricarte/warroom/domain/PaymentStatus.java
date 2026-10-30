package br.com.ricarte.warroom.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Financial state of a job payment, named after what the provider confirmed, never after what the
 * platform wishes. {@code released} and {@code refunded} are only reached when the provider
 * confirmed the remote operation (or in demo mode, which is never production).
 */
public enum PaymentStatus {
    /** Checkout created with the provider; nothing was charged yet. */
    checkout_created,
    /** Provider confirmed the funds were received by the platform (webhook verified). */
    paid,
    /** The job was completed; the transfer to the professional was requested but not confirmed. */
    payout_requested,
    /** Provider confirmed the transfer to the professional. */
    released,
    /** A refund to the company was requested but not confirmed. */
    refund_requested,
    /** Provider confirmed the refund. */
    refunded,
    /** A dispute or chargeback blocks any release until support resolves it. */
    disputed,
    /** The checkout expired without payment; a new checkout may be created. */
    expired,
    /** The provider reported a failed asynchronous payment; a new checkout may be created. */
    failed;

    public Set<PaymentStatus> next() {
        return switch (this) {
            case checkout_created -> EnumSet.of(paid, expired, failed);
            case paid -> EnumSet.of(payout_requested, refund_requested, disputed);
            case payout_requested -> EnumSet.of(released, disputed);
            case refund_requested -> EnumSet.of(refunded, disputed);
            case disputed -> EnumSet.of(payout_requested, refund_requested);
            case expired, failed -> EnumSet.of(checkout_created);
            case released, refunded -> EnumSet.noneOf(PaymentStatus.class);
        };
    }

    public boolean canTransitionTo(PaymentStatus target) {
        return next().contains(target);
    }

    /** States in which the money is with the platform and nothing else was started. */
    public boolean fundsHeld() {
        return this == paid;
    }

    public boolean retryableCheckout() {
        return this == expired || this == failed;
    }
}
