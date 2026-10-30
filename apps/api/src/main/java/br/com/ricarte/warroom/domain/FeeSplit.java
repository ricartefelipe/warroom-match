package br.com.ricarte.warroom.domain;

/**
 * Split of a payment between platform and professional, in integer cents. The platform fee is
 * rounded down (floor) and the professional receives the remainder, so the parts always add up
 * to the amount. Amounts above {@link #MAX_AMOUNT_CENTS} are rejected before any arithmetic.
 */
public record FeeSplit(long amountCents, long platformFeeCents, long proAmountCents) {

    public static final long MAX_AMOUNT_CENTS = 10_000_000_000_00L;
    public static final int BPS_SCALE = 10_000;

    public FeeSplit {
        if (amountCents < 0 || platformFeeCents < 0 || proAmountCents < 0) {
            throw new IllegalArgumentException("fee split parts must not be negative");
        }
        if (platformFeeCents + proAmountCents != amountCents) {
            throw new IllegalArgumentException("fee split parts must add up to the amount");
        }
    }

    public static FeeSplit of(long amountCents, int platformFeeBps) {
        if (amountCents <= 0 || amountCents > MAX_AMOUNT_CENTS) {
            throw new IllegalArgumentException("amount must be between 1 and " + MAX_AMOUNT_CENTS + " cents");
        }
        if (platformFeeBps < 0 || platformFeeBps > BPS_SCALE) {
            throw new IllegalArgumentException("platform fee must be between 0 and " + BPS_SCALE + " bps");
        }
        long platformFee = Math.multiplyExact(amountCents, platformFeeBps) / BPS_SCALE;
        return new FeeSplit(amountCents, platformFee, amountCents - platformFee);
    }
}
