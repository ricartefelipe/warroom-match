package br.com.ricarte.warroom.domain;

public enum AccountRole {
    company,
    pro,
    unset;

    public static AccountRole parse(String value) {
        if (value == null || value.isBlank()) {
            return unset;
        }
        return AccountRole.valueOf(value);
    }
}
