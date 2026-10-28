package io.github.vbaggio.sefaz.mock.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.vbaggio.sefaz.mock.store.NotaRecord;
import java.time.OffsetDateTime;

/** Estado de uma nota em {@code GET /__admin/notas/{chave}}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotaView(
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

    public static NotaView of(NotaRecord nota, boolean withXml) {
        return new NotaView(nota.chave().valor(), nota.tpAmb(), nota.cStat(), nota.xMotivo(), nota.nProt(),
                nota.dhRecbto(), nota.digVal(), nota.scenario(), nota.naBase(), withXml ? nota.xml() : null);
    }
}
