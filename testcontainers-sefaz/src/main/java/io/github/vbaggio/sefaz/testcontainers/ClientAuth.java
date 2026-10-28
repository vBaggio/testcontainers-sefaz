package io.github.vbaggio.sefaz.testcontainers;

/** Autenticação de cliente na porta HTTPS. Qualquer certificado de cliente é aceito. */
public enum ClientAuth {
    /** Não pede certificado. */
    NONE,
    /** Pede, mas aceita conexão sem certificado (padrão). */
    WANT,
    /** Recusa conexão sem certificado. */
    NEED
}
