package io.github.vbaggio.sefaz.mock.handler;

import io.github.vbaggio.sefaz.mock.nfe.SefazClock;
import io.github.vbaggio.sefaz.mock.store.NotaRecord;
import io.github.vbaggio.sefaz.mock.xml.Template;
import java.time.OffsetDateTime;
import java.util.Map;

/** Montagem do elemento {@code protNFe}. */
final class Protocolos {

    private Protocolos() {
    }

    /** Protocolo de uma nota na base: autorizada ou denegada. */
    static String deNota(NotaRecord nota, String verAplic) {
        return Template.load("protNFe").render(Map.of(
                "tpAmb", nota.tpAmb(),
                "verAplic", verAplic,
                "chNFe", nota.chave().valor(),
                "dhRecbto", SefazClock.format(nota.dhRecbto()),
                "nProt", nota.nProt(),
                "digVal", nota.digVal() == null ? "" : nota.digVal(),
                "cStat", nota.cStat(),
                "xMotivo", nota.xMotivo()));
    }

    /** Rejeição: sem número de protocolo nem {@code digVal}. */
    static String rejeicao(String tpAmb, String verAplic, String chNFe, OffsetDateTime dhRecbto, String cStat,
            String xMotivo) {
        return Template.load("protNFe-rejeicao").render(Map.of(
                "tpAmb", tpAmb,
                "verAplic", verAplic,
                "chNFe", chNFe,
                "dhRecbto", SefazClock.format(dhRecbto),
                "cStat", cStat,
                "xMotivo", xMotivo));
    }
}
