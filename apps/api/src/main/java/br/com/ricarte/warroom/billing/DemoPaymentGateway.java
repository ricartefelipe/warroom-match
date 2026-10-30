package br.com.ricarte.warroom.billing;

import br.com.ricarte.warroom.domain.Job;
import br.com.ricarte.warroom.domain.JobPayment;
import br.com.ricarte.warroom.domain.Profile;

import java.util.Optional;
import java.util.UUID;

/** Simulated provider for demonstrations. Every operation "succeeds" at once; nothing is real. */
public class DemoPaymentGateway implements PaymentGateway {

    static final String PREFIX = "demo_";

    @Override
    public Checkout createCheckout(Job job, JobPayment payment) {
        return new Checkout(PREFIX + "cs_" + UUID.randomUUID(), null, true);
    }

    @Override
    public Optional<Confirmation> requestPayout(JobPayment payment, Profile professional) {
        return Optional.of(new Confirmation(PREFIX + "tr_" + payment.getId()));
    }

    @Override
    public Optional<Confirmation> requestRefund(JobPayment payment) {
        return Optional.of(new Confirmation(PREFIX + "re_" + payment.getId()));
    }

    @Override
    public RemoteState fetchState(JobPayment payment) {
        return new RemoteState(
                payment.getPaidAt() != null,
                payment.getStripeTransferId() != null,
                payment.getStripeRefundId() != null,
                "demo provider: local state is the only state");
    }
}
