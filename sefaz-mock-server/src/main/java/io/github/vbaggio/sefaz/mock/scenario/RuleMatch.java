package io.github.vbaggio.sefaz.mock.scenario;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.vbaggio.sefaz.mock.nfe.ChaveAcesso;
import io.github.vbaggio.sefaz.mock.nfe.Numeracao;
import java.util.Locale;
import java.util.Objects;

/**
 * Critério de uma regra. Campos nulos não restringem. Série e número são comparados sem zeros
 * à esquerda; o CNPJ, sem pontuação.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuleMatch(String operation, String chave, String cnpj, String serie, String numero) {

    public static final RuleMatch ANY = new RuleMatch(null, null, null, null, null);

    public RuleMatch {
        operation = operation == null ? null : operation.trim().toUpperCase(Locale.ROOT);
        chave = chave == null ? null : ChaveAcesso.parse(chave).valor();
        cnpj = Numeracao.normalizaCnpj(cnpj);
        serie = Numeracao.normaliza(serie);
        numero = Numeracao.normaliza(numero);
    }

    /** Regra "da nota": casa por chave ou por campos da chave, e tem prioridade. */
    public boolean hasNotaFields() {
        return chave != null || cnpj != null || serie != null || numero != null;
    }

    public boolean matchesOperation(String op) {
        return operation == null || operation.equals(op);
    }

    public boolean matchesNota(ChaveAcesso nota) {
        return (chave == null || chave.equals(nota.valor()))
                && (cnpj == null || cnpj.equals(nota.cnpj()))
                && (serie == null || serie.equals(Numeracao.normaliza(nota.serie())))
                && (numero == null || numero.equals(Numeracao.normaliza(nota.numero())));
    }

    static RuleMatch orAny(RuleMatch match) {
        return Objects.requireNonNullElse(match, ANY);
    }
}
