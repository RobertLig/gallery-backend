package com.robert.gallery.domain;

import java.util.Locale;

public enum UserLocale {
    EN("en"),
    PL("pl");

    private final String languageTag;

    UserLocale(String languageTag) {
        this.languageTag = languageTag;
    }

    public static UserLocale from(Locale locale) {
        return "pl".equals(locale.getLanguage()) ? PL : EN;
    }

    public String languageTag() {
        return languageTag;
    }
}
