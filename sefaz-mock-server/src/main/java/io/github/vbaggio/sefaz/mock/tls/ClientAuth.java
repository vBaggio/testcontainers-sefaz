package io.github.vbaggio.sefaz.mock.tls;

import java.util.Locale;

/** Autenticação de cliente na porta HTTPS, definida por {@code SEFAZ_CLIENT_AUTH}. */
public enum ClientAuth {
    NONE,
    WANT,
    NEED;

    public static ClientAuth parse(String value) {
        if (value == null || value.isBlank()) {
            return WANT;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("SEFAZ_CLIENT_AUTH deve ser none, want ou need: " + value);
        }
    }
}
