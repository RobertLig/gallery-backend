package com.robert.gallery.i18n;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.HttpHeaders;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

@ApplicationScoped
public class MessageService {

    public String get(String key, HttpHeaders headers) {
        return get(key, resolveLocale(headers.getAcceptableLanguages()));
    }

    public String get(String key, Locale locale) {
        ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", locale);
        return bundle.getString(key);
    }

    private Locale resolveLocale(List<Locale> acceptableLanguages) {
        for (Locale locale : acceptableLanguages) {
            String language = locale.getLanguage();
            if ("pl".equals(language)) {
                return Locale.forLanguageTag("pl");
            }
            if ("en".equals(language)) {
                return Locale.ENGLISH;
            }
        }
        return Locale.ENGLISH;
    }
}
