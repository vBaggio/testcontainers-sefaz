package io.github.vbaggio.sefaz.mock.store;

import io.github.vbaggio.sefaz.mock.nfe.ChaveAcesso;
import io.github.vbaggio.sefaz.mock.nfe.Motivo;
import java.time.OffsetDateTime;

/**
 * Estado de uma nota recebida: o XML como chegou, o resultado e o cenário que o produziu.
 *
 * @param nProt número do protocolo; {@code null} quando a nota foi rejeitada
 * @param digVal {@code DigestValue} da assinatura; vazio se a nota veio sem assinatura
 */
public record NotaRecord(
        ChaveAcesso chave,
        String tpAmb,
        String xml,
        String nProt,
        String cStat,
        String xMotivo,
        OffsetDateTime dhRecbto,
        String digVal,
        String scenario) {

    /** Autorizada ou denegada: existe na base e consome a numeração. */
    public boolean naBase() {
        return Motivo.consumeNumeracao(cStat);
    }
}
