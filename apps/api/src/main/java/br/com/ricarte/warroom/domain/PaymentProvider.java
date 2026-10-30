package br.com.ricarte.warroom.domain;

/** Who holds the money. {@code demo} never exists in production. */
public enum PaymentProvider {
    demo,
    stripe
}
