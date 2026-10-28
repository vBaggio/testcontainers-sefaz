package io.github.vbaggio.sefaz.mock.store;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lote recebido no modo assíncrono. Os resultados das notas já ficam decididos no envio; a
 * consulta do recibo só os devolve.
 */
public final class Recibo {

    private final String nRec;
    private final String tpAmb;
    private final String cUF;
    private final OffsetDateTime dhRecbto;
    private final List<String> chaves;
    private final List<String> protocolosXml;
    private final AtomicInteger consultasEmProcessamento = new AtomicInteger();

    public Recibo(String nRec, String tpAmb, String cUF, OffsetDateTime dhRecbto, List<String> chaves,
            List<String> protocolosXml) {
        this.nRec = nRec;
        this.tpAmb = tpAmb;
        this.cUF = cUF;
        this.dhRecbto = dhRecbto;
        this.chaves = List.copyOf(chaves);
        this.protocolosXml = List.copyOf(protocolosXml);
    }

    public String nRec() {
        return nRec;
    }

    public String tpAmb() {
        return tpAmb;
    }

    public String cUF() {
        return cUF;
    }

    public OffsetDateTime dhRecbto() {
        return dhRecbto;
    }

    public List<String> chaves() {
        return chaves;
    }

    /** Elementos {@code protNFe} prontos, na ordem das notas do lote. */
    public List<String> protocolosXml() {
        return protocolosXml;
    }

    /** Conta mais uma consulta respondida com "lote em processamento" e devolve o total. */
    public int registraConsultaEmProcessamento() {
        return consultasEmProcessamento.incrementAndGet();
    }

    public int consultasEmProcessamento() {
        return consultasEmProcessamento.get();
    }
}
