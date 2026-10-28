package io.github.vbaggio.sefaz.mock.nfe;

/** Identidade de numeração de uma nota: CNPJ do emitente, modelo, série e número. */
public record Numeracao(String cnpj, String modelo, String serie, String numero) {

    /** Remove zeros à esquerda para que "001" e "1" sejam a mesma série. */
    public static String normaliza(String valor) {
        if (valor == null) {
            return null;
        }
        String trimmed = valor.trim();
        if (trimmed.isEmpty() || !trimmed.chars().allMatch(Character::isDigit)) {
            return trimmed;
        }
        String semZeros = trimmed.replaceFirst("^0+(?=.)", "");
        return semZeros;
    }

    public static String normalizaCnpj(String cnpj) {
        return cnpj == null ? null : cnpj.replaceAll("[^0-9A-Za-z]", "").toUpperCase(java.util.Locale.ROOT);
    }
}
