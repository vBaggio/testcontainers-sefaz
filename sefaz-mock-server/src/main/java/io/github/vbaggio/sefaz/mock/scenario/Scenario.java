package io.github.vbaggio.sefaz.mock.scenario;

import static io.github.vbaggio.sefaz.mock.handler.Operations.AUTORIZACAO;
import static io.github.vbaggio.sefaz.mock.handler.Operations.RET_AUTORIZACAO;
import static io.github.vbaggio.sefaz.mock.handler.Operations.STATUS_SERVICO;

import java.util.Set;

/**
 * Catálogo de cenários. Os nomes e parâmetros são contrato público versionado (ADR-004):
 * renomear um cenário ou mudar seu comportamento é breaking change.
 *
 * <p>Cada cenário declara as operações a que se aplica. Uma regra cujo cenário não se aplica à
 * operação da requisição é ignorada e não consome {@code times} (ADR-006).
 */
public enum Scenario {

    /** Autoriza normalmente. */
    HAPPY_PATH(Set.of()),
    /** Autoriza e grava a nota, depois segura a resposta por {@code delayMs}. */
    TIMEOUT_AFTER_AUTHORIZE(Set.of(AUTORIZACAO)),
    /** Segura a resposta por {@code delayMs}, não grava nada e responde 504. */
    TIMEOUT_BEFORE_AUTHORIZE(Set.of(AUTORIZACAO)),
    /** Autoriza, grava e fecha a conexão sem responder. */
    CONNECTION_RESET_AFTER_AUTHORIZE(Set.of(AUTORIZACAO)),
    /** Status e autorização devolvem 108. */
    SERVICE_PAUSED(Set.of(STATUS_SERVICO, AUTORIZACAO)),
    /** HTTP 503 com SOAP Fault. */
    SERVICE_UNAVAILABLE(Set.of()),
    /** Retorno assíncrono devolve 105 por {@code attempts} consultas, depois 104. */
    PROCESSING(Set.of(RET_AUTORIZACAO)),
    /** Protocolo com uso denegado ({@code cStat}, padrão 301) e nota gravada como denegada. */
    DENIED(Set.of(AUTORIZACAO)),
    /** Rejeição com {@code cStat} e {@code xMotivo} dos parâmetros. */
    REJECT(Set.of()),
    /** Responde normalmente após {@code delayMs}. */
    SLOW(Set.of());

    private final Set<String> operations;

    Scenario(Set<String> operations) {
        this.operations = operations;
    }

    /** Conjunto vazio significa todas as operações, inclusive as de handlers plugados. */
    public boolean appliesTo(String operation) {
        return operations.isEmpty() || operations.contains(operation);
    }

    public Set<String> operations() {
        return operations;
    }

    public static Scenario parse(String name) {
        try {
            return Scenario.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Cenário desconhecido: " + name);
        }
    }
}
