package io.github.vbaggio.sefaz.mock.nfe;

/**
 * Códigos de status ({@code cStat}) e textos ({@code xMotivo}) usados nas respostas padrão.
 *
 * <p>Textos conforme a tabela de códigos de erros e mensagens do MOC 7.0 (ADR-005). Textos que
 * levam dados variáveis na SEFAZ real são montados pelos métodos estáticos abaixo.
 */
public final class Motivo {

    public static final String AUTORIZADO = "100";
    public static final String LOTE_RECEBIDO = "103";
    public static final String LOTE_PROCESSADO = "104";
    public static final String LOTE_EM_PROCESSAMENTO = "105";
    public static final String LOTE_NAO_LOCALIZADO = "106";
    public static final String SERVICO_EM_OPERACAO = "107";
    public static final String SERVICO_PARALISADO = "108";
    public static final String USO_DENEGADO = "110";
    public static final String DUPLICIDADE = "204";
    public static final String NOTA_DENEGADA = "205";
    public static final String FALHA_SCHEMA = "215";
    public static final String NAO_CONSTA_NA_BASE = "217";
    public static final String FALHA_SCHEMA_LOTE = "225";
    public static final String DENEGADO_EMITENTE = "301";
    public static final String DENEGADO_DESTINATARIO = "302";
    public static final String DENEGADO_DESTINATARIO_NAO_HABILITADO = "303";
    public static final String DUPLICIDADE_CHAVE_DIFERENTE = "539";

    private Motivo() {
    }

    /** Texto padrão de um código; códigos desconhecidos recebem um texto genérico. */
    public static String texto(String cStat) {
        return switch (cStat) {
            case AUTORIZADO -> "Autorizado o uso da NF-e";
            case LOTE_RECEBIDO -> "Lote recebido com sucesso";
            case LOTE_PROCESSADO -> "Lote processado";
            case LOTE_EM_PROCESSAMENTO -> "Lote em processamento";
            case LOTE_NAO_LOCALIZADO -> "Lote não localizado";
            case SERVICO_EM_OPERACAO -> "Serviço em Operação";
            case SERVICO_PARALISADO -> "Serviço Paralisado Momentaneamente (curto prazo)";
            case USO_DENEGADO -> "Uso Denegado";
            case FALHA_SCHEMA -> "Rejeição: Falha no schema XML";
            case NAO_CONSTA_NA_BASE -> "Rejeição: NF-e não consta na base de dados da SEFAZ";
            case FALHA_SCHEMA_LOTE -> "Rejeição: Falha no Schema XML do lote de NFe";
            case DENEGADO_EMITENTE -> "Uso Denegado: Irregularidade fiscal do emitente";
            case DENEGADO_DESTINATARIO -> "Uso Denegado: Irregularidade fiscal do destinatário";
            case DENEGADO_DESTINATARIO_NAO_HABILITADO -> "Uso Denegado: Destinatário não habilitado a operar na UF";
            default -> "Rejeição simulada pelo sefaz-mock-server";
        };
    }

    public static String duplicidade(String nProt, String dhAut) {
        return "Rejeição: Duplicidade de NF-e [nProt:" + nProt + "][dhAut:" + dhAut + "]";
    }

    public static String notaDenegada(String nProt, String dhDeneg) {
        return "Rejeição: NF-e está denegada na base de dados da SEFAZ [nProt:" + nProt + "][dhDeneg:" + dhDeneg + "]";
    }

    public static String duplicidadeChaveDiferente(String chNFe, String nProt, String dhAut) {
        return "Rejeição: Duplicidade de NF-e com diferença na Chave de Acesso [chNFe:" + chNFe + "][nProt:"
                + nProt + "][dhAut:" + dhAut + "]";
    }

    /** Códigos que fazem a nota existir na base da SEFAZ: autorizada ou denegada. */
    public static boolean consumeNumeracao(String cStat) {
        return AUTORIZADO.equals(cStat) || isDenegacao(cStat);
    }

    public static boolean isDenegacao(String cStat) {
        return USO_DENEGADO.equals(cStat)
                || DENEGADO_EMITENTE.equals(cStat)
                || DENEGADO_DESTINATARIO.equals(cStat)
                || DENEGADO_DESTINATARIO_NAO_HABILITADO.equals(cStat);
    }
}
