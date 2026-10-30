package br.com.ricarte.warroom.billing;

import br.com.ricarte.warroom.domain.Job;
import br.com.ricarte.warroom.domain.JobPayment;
import br.com.ricarte.warroom.domain.Profile;

import java.util.Optional;

/**
 * Operations the payment provider performs. Each one is a distinct remote operation with its own
 * confirmation; the service only records a state the provider confirmed.
 */
public interface PaymentGateway {

    /** Creates a checkout the company pays. Idempotent per payment attempt. */
    Checkout createCheckout(Job job, JobPayment payment);

    /**
     * Requests the transfer of the professional's share. Returns the confirmed transfer, or empty
     * when the provider cannot do it now (for example, the professional has no payout account).
     */
    Optional<Confirmation> requestPayout(JobPayment payment, Profile professional);

    /** Requests the refund of the full amount to the company. */
    Optional<Confirmation> requestRefund(JobPayment payment);

    /** Asks the provider what it knows about this payment, for reconciliation. */
    RemoteState fetchState(JobPayment payment);

    record Checkout(String sessionId, String url, boolean paidImmediately) {
    }

    record Confirmation(String remoteId) {
    }

    record RemoteState(boolean paid, boolean transferred, boolean refunded, String detail) {
    }
}
