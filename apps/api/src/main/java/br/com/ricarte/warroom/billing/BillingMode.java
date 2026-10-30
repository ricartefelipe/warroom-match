package br.com.ricarte.warroom.billing;

/** How money is handled. Chosen explicitly by configuration; never inferred from missing keys. */
public enum BillingMode {
    demo,
    stripe
}
