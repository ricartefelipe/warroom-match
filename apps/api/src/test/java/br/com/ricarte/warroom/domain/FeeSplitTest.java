package br.com.ricarte.warroom.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeeSplitTest {

    @Test
    void splitsEighteenPercentAndRoundsTheFeeDown() {
        FeeSplit split = FeeSplit.of(50000, 1800);
        assertThat(split.platformFeeCents()).isEqualTo(9000);
        assertThat(split.proAmountCents()).isEqualTo(41000);

        FeeSplit odd = FeeSplit.of(20001, 1800);
        assertThat(odd.platformFeeCents()).isEqualTo(3600);
        assertThat(odd.proAmountCents()).isEqualTo(16401);
        assertThat(odd.platformFeeCents() + odd.proAmountCents()).isEqualTo(20001);
    }

    @Test
    void zeroAndFullFeeAreValidEdges() {
        assertThat(FeeSplit.of(1000, 0).proAmountCents()).isEqualTo(1000);
        assertThat(FeeSplit.of(1000, 10_000).proAmountCents()).isEqualTo(0);
    }

    @Test
    void rejectsZeroNegativeOversizedAmountsAndBadBps() {
        assertThatThrownBy(() -> FeeSplit.of(0, 1800)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FeeSplit.of(-1, 1800)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FeeSplit.of(FeeSplit.MAX_AMOUNT_CENTS + 1, 1800)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FeeSplit.of(1000, 10_001)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FeeSplit(100, 50, 40)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jobAndPaymentMachinesHaveNoWayBackFromTerminalStates() {
        assertThat(JobStatus.completed.terminal()).isTrue();
        assertThat(JobStatus.cancelled.canTransitionTo(JobStatus.open)).isFalse();
        assertThat(PaymentStatus.released.next()).isEmpty();
        assertThat(PaymentStatus.refunded.next()).isEmpty();
        assertThat(PaymentStatus.paid.canTransitionTo(PaymentStatus.released)).isFalse();
    }
}
