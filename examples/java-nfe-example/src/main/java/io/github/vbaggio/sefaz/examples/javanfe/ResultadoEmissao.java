package io.github.vbaggio.sefaz.examples.javanfe;

/**
 * Resultado de uma emissão.
 *
 * @param tentativas quantas vezes o lote foi enviado
 * @param recuperadaPorConsulta o envio falhou na comunicação e o protocolo veio da consulta
 */
public record ResultadoEmissao(
        Status status,
        String chave,
        String cStat,
        String xMotivo,
        String nProt,
        int tentativas,
        boolean recuperadaPorConsulta) {

    public enum Status {
        AUTORIZADA,
        DENEGADA,
        REJEITADA,
        /** A SEFAZ não está atendendo: o emissor deve entrar em contingência. */
        CONTINGENCIA
    }
}
