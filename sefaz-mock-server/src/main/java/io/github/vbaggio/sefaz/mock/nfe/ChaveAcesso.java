package io.github.vbaggio.sefaz.mock.nfe;

import java.util.Locale;

/**
 * Chave de acesso de 44 posições decomposta por posição.
 *
 * <p>O CNPJ é mantido como texto para aceitar o CNPJ alfanumérico. O DV não é conferido: o mock
 * não é validador (ADR-001).
 */
public record ChaveAcesso(
        String valor,
        String cUF,
        String aamm,
        String cnpj,
        String modelo,
        String serie,
        String numero,
        String tpEmis,
        String cNF,
        String dv) {

    public static final int TAMANHO = 44;

    public static ChaveAcesso parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Chave de acesso ausente");
        }
        String chave = raw.trim();
        if (chave.regionMatches(true, 0, "NFe", 0, 3)) {
            chave = chave.substring(3);
        }
        chave = chave.toUpperCase(Locale.ROOT);
        if (chave.length() != TAMANHO || !chave.chars().allMatch(Character::isLetterOrDigit)) {
            throw new IllegalArgumentException("Chave de acesso deve ter 44 posições: " + raw);
        }
        return new ChaveAcesso(
                chave,
                chave.substring(0, 2),
                chave.substring(2, 6),
                chave.substring(6, 20),
                chave.substring(20, 22),
                chave.substring(22, 25),
                chave.substring(25, 34),
                chave.substring(34, 35),
                chave.substring(35, 43),
                chave.substring(43, 44));
    }

    public static boolean isValida(String raw) {
        try {
            parse(raw);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** CNPJ + modelo + série + número, a numeração que a SEFAZ não deixa repetir. */
    public Numeracao numeracao() {
        return new Numeracao(cnpj, modelo, Numeracao.normaliza(serie), Numeracao.normaliza(numero));
    }

    @Override
    public String toString() {
        return valor;
    }
}
