package io.github.vbaggio.sefaz.testcontainers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;

/**
 * Estado de uma nota na SEFAZ fake.
 *
 * @param naBase autorizada ou denegada: a consulta de protocolo encontra a nota
 * @param xml a nota como foi recebida (o elemento {@code NFe})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotaState(
        String chave,
        String tpAmb,
        String cStat,
        String xMotivo,
        String nProt,
        OffsetDateTime dhRecbto,
        String digVal,
        String scenario,
        boolean naBase,
        String xml) {

    public boolean autorizada() {
        return "100".equals(cStat);
    }
}
