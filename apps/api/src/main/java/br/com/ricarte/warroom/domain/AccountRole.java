package br.com.ricarte.warroom.domain;

public enum AccountRole {
    company,
    pro,
    admin,
    unset;

    /** Roles an account may choose for itself. {@code admin} is only granted by configuration. */
    public boolean selfAssignable() {
        return this == company || this == pro;
    }

    public static AccountRole parse(String value) {
        if (value == null || value.isBlank()) {
            return unset;
        }
        return AccountRole.valueOf(value);
    }
}
