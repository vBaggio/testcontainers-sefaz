package io.github.vbaggio.sefaz.testcontainers;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Cenário de comportamento da SEFAZ fake, com seus parâmetros. Os nomes são contrato público
 * versionado (ADR-004).
 */
public final class Scenario {

    private final String name;
    private final Map<String, Object> params;

    private Scenario(String name, Map<String, Object> params) {
        this.name = Objects.requireNonNull(name, "name");
        Map<String, Object> normalized = new LinkedHashMap<>();
        // Inteiros viram Long: o cenário lido da API admin é igual ao registrado.
        params.forEach((k, v) -> normalized.put(k, v instanceof Integer || v instanceof Short || v instanceof Byte
                ? Long.valueOf(((Number) v).longValue())
                : v));
        this.params = Map.copyOf(normalized);
    }

    /** Cenário pelo nome, para cenários novos do servidor ainda sem método aqui. */
    public static Scenario of(String name, Map<String, ?> params) {
        return new Scenario(name, new LinkedHashMap<>(params));
    }

    /** Autoriza normalmente. */
    public static Scenario happyPath() {
        return of("HAPPY_PATH", Map.of());
    }

    /** Autoriza e grava a nota, depois segura a resposta. A consulta de protocolo devolve 100. */
    public static Scenario timeoutAfterAuthorize(Duration delay) {
        return of("TIMEOUT_AFTER_AUTHORIZE", Map.of("delayMs", delay.toMillis()));
    }

    /** Segura a resposta, não grava nada e responde 504. A consulta devolve 217; reenviar é seguro. */
    public static Scenario timeoutBeforeAuthorize(Duration delay) {
        return of("TIMEOUT_BEFORE_AUTHORIZE", Map.of("delayMs", delay.toMillis()));
    }

    /** Autoriza, grava e fecha a conexão sem responder. */
    public static Scenario connectionResetAfterAuthorize() {
        return of("CONNECTION_RESET_AFTER_AUTHORIZE", Map.of());
    }

    /** Status e autorização devolvem 108 (serviço paralisado). */
    public static Scenario servicePaused() {
        return of("SERVICE_PAUSED", Map.of());
    }

    /** HTTP 503 com SOAP Fault. */
    public static Scenario serviceUnavailable() {
        return of("SERVICE_UNAVAILABLE", Map.of());
    }

    /** A consulta do recibo devolve 105 (lote em processamento) {@code attempts} vezes, depois 104. */
    public static Scenario processing(int attempts) {
        return of("PROCESSING", Map.of("attempts", attempts));
    }

    /** Uso denegado com 301 (irregularidade fiscal do emitente). */
    public static Scenario denied() {
        return of("DENIED", Map.of());
    }

    /** Uso denegado com o {@code cStat} informado (110, 301, 302 ou 303). */
    public static Scenario denied(String cStat) {
        return of("DENIED", Map.of("cStat", cStat));
    }

    /** Rejeição da nota com o {@code cStat} e o texto informados. */
    public static Scenario reject(String cStat, String xMotivo) {
        return of("REJECT", Map.of("cStat", cStat, "xMotivo", xMotivo));
    }

    /** Rejeição do lote inteiro, sem {@code protNFe}. */
    public static Scenario rejectLote(String cStat, String xMotivo) {
        return of("REJECT", Map.of("cStat", cStat, "xMotivo", xMotivo, "nivel", "lote"));
    }

    /** Responde normalmente depois do atraso. */
    public static Scenario slow(Duration delay) {
        return of("SLOW", Map.of("delayMs", delay.toMillis()));
    }

    public String name() {
        return name;
    }

    public Map<String, Object> params() {
        return params;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Scenario other && name.equals(other.name) && params.equals(other.params);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, params);
    }

    @Override
    public String toString() {
        return params.isEmpty() ? name : name + params;
    }
}
