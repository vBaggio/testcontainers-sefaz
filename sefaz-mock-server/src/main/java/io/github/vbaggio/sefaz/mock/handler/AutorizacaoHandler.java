package io.github.vbaggio.sefaz.mock.handler;

import io.github.vbaggio.sefaz.mock.nfe.ChaveAcesso;
import io.github.vbaggio.sefaz.mock.nfe.Motivo;
import io.github.vbaggio.sefaz.mock.nfe.SefazClock;
import io.github.vbaggio.sefaz.mock.scenario.Scenario;
import io.github.vbaggio.sefaz.mock.scenario.ScenarioContext;
import io.github.vbaggio.sefaz.mock.soap.SoapRequest;
import io.github.vbaggio.sefaz.mock.soap.SoapResponse;
import io.github.vbaggio.sefaz.mock.store.NotaRecord;
import io.github.vbaggio.sefaz.mock.store.NotaStore;
import io.github.vbaggio.sefaz.mock.store.Recibo;
import io.github.vbaggio.sefaz.mock.xml.SecureXml;
import io.github.vbaggio.sefaz.mock.xml.Template;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * NFeAutorizacao4 / {@code nfeAutorizacaoLote}, modos síncrono ({@code indSinc=1}) e assíncrono.
 *
 * <p>O corpo da nota é opaco (ADR-001): só a chave, {@code tpAmb}, {@code mod}, {@code tpEmis} e o
 * {@code DigestValue} são lidos.
 */
public final class AutorizacaoHandler extends NfeHandler {

    private static final String NFE = child("NFe");

    /** Verificação de duplicidade e gravação precisam ser atômicas entre requisições. */
    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public String wsdlNamespace() {
        return Operations.WSDL_BASE + "NFeAutorizacao4";
    }

    @Override
    public String operation() {
        return Operations.AUTORIZACAO;
    }

    @Override
    public String xsdFileName() {
        return "enviNFe_v4.00.xsd";
    }

    @Override
    public String schemaFailureCStat() {
        return Motivo.FALHA_SCHEMA_LOTE;
    }

    @Override
    public List<ChaveAcesso> chaves(SoapRequest request, NotaStore store) {
        List<ChaveAcesso> chaves = new ArrayList<>();
        for (NotaLida nota : notas(request)) {
            nota.chave().ifPresent(chaves::add);
        }
        return chaves;
    }

    @Override
    public SoapResponse handle(SoapRequest request, ScenarioContext scenario, NotaStore store) {
        OffsetDateTime agora = clock().now();
        List<NotaLida> notas = notas(request);
        String tpAmb = notas.isEmpty() ? TP_AMB_HOMOLOGACAO : notas.get(0).tpAmb();
        String cUF = notas.stream().flatMap(n -> n.chave().stream()).map(ChaveAcesso::cUF).findFirst().orElse("");
        boolean sincrono = "1".equals(request.text(child("indSinc")));

        if (notas.isEmpty() || notas.stream().anyMatch(n -> n.chave().isEmpty())) {
            return lote(tpAmb, cUF, agora, Motivo.FALHA_SCHEMA_LOTE, Motivo.texto(Motivo.FALHA_SCHEMA_LOTE), "", notas);
        }
        if (scenario.is(Scenario.SERVICE_PAUSED)) {
            return lote(tpAmb, cUF, agora, Motivo.SERVICO_PARALISADO, Motivo.texto(Motivo.SERVICO_PARALISADO), "",
                    notas);
        }
        if (scenario.is(Scenario.REJECT) && scenario.rejeitaLote()) {
            String cStat = scenario.cStat(Motivo.FALHA_SCHEMA_LOTE);
            return lote(tpAmb, cUF, agora, cStat, scenario.xMotivo(Motivo.texto(cStat)), "", notas);
        }

        List<Resultado> resultados = new ArrayList<>();
        lock.lock();
        try {
            for (NotaLida nota : notas) {
                resultados.add(processa(nota, scenario, store, agora));
            }
        } finally {
            lock.unlock();
        }
        List<String> protocolos = resultados.stream().map(Resultado::protNFe).toList();

        if (sincrono) {
            String xml = Template.load("retEnviNFe").render(Map.of(
                    "tpAmb", tpAmb,
                    "verAplic", verAplic(),
                    "cStat", Motivo.LOTE_PROCESSADO,
                    "xMotivo", Motivo.texto(Motivo.LOTE_PROCESSADO),
                    "cUF", cUF,
                    "dhRecbto", SefazClock.format(agora),
                    "resultado", String.join("", protocolos)));
            // No histórico, o cStat que interessa no modo síncrono é o da nota.
            Resultado primeiro = resultados.get(0);
            return SoapResponse.ok(xml, primeiro.cStat(), primeiro.xMotivo(), chavesDe(notas));
        }

        String nRec = store.nextRecibo(cUF);
        store.saveRecibo(new Recibo(nRec, tpAmb, cUF, agora, chavesDe(notas), protocolos));
        String infRec = Template.load("infRec").render(Map.of("nRec", nRec));
        return lote(tpAmb, cUF, agora, Motivo.LOTE_RECEBIDO, Motivo.texto(Motivo.LOTE_RECEBIDO), infRec, notas);
    }

    private Resultado processa(NotaLida nota, ScenarioContext scenario, NotaStore store, OffsetDateTime agora) {
        ChaveAcesso chave = nota.chave().orElseThrow();
        Optional<NotaRecord> mesmaChave = store.find(chave.valor()).filter(NotaRecord::naBase);
        if (mesmaChave.isPresent()) {
            NotaRecord existente = mesmaChave.get();
            String dh = SefazClock.format(existente.dhRecbto());
            return Motivo.isDenegacao(existente.cStat())
                    ? rejeita(nota, agora, Motivo.NOTA_DENEGADA, Motivo.notaDenegada(existente.nProt(), dh))
                    : rejeita(nota, agora, Motivo.DUPLICIDADE, Motivo.duplicidade(existente.nProt(), dh));
        }
        Optional<NotaRecord> mesmaNumeracao = store.findByNumeracao(chave.numeracao());
        if (mesmaNumeracao.isPresent()) {
            NotaRecord existente = mesmaNumeracao.get();
            return rejeita(nota, agora, Motivo.DUPLICIDADE_CHAVE_DIFERENTE, Motivo.duplicidadeChaveDiferente(
                    existente.chave().valor(), existente.nProt(), SefazClock.format(existente.dhRecbto())));
        }

        if (scenario.is(Scenario.REJECT)) {
            String cStat = scenario.cStat(Motivo.FALHA_SCHEMA);
            String xMotivo = scenario.xMotivo(Motivo.texto(cStat));
            store.save(new NotaRecord(chave, nota.tpAmb(), nota.xml(), null, cStat, xMotivo, agora, nota.digVal(),
                    scenario.scenario().name()));
            return rejeita(nota, agora, cStat, xMotivo);
        }

        String cStat = scenario.is(Scenario.DENIED)
                ? scenario.cStat(ScenarioContext.DEFAULT_DENIED_CSTAT)
                : Motivo.AUTORIZADO;
        String xMotivo = scenario.is(Scenario.DENIED) ? scenario.xMotivo(Motivo.texto(cStat)) : Motivo.texto(cStat);
        NotaRecord gravada = new NotaRecord(chave, nota.tpAmb(), nota.xml(),
                store.nextProtocolo(chave.cUF(), agora.getYear()), cStat, xMotivo, agora, nota.digVal(),
                scenario.scenario().name());
        store.save(gravada);
        return new Resultado(Protocolos.deNota(gravada, verAplic()), cStat, xMotivo);
    }

    private Resultado rejeita(NotaLida nota, OffsetDateTime agora, String cStat, String xMotivo) {
        String xml = Protocolos.rejeicao(nota.tpAmb(), verAplic(), nota.chave().orElseThrow().valor(), agora, cStat,
                xMotivo);
        return new Resultado(xml, cStat, xMotivo);
    }

    private SoapResponse lote(String tpAmb, String cUF, OffsetDateTime agora, String cStat, String xMotivo,
            String resultado, List<NotaLida> notas) {
        String xml = Template.load("retEnviNFe").render(Map.of(
                "tpAmb", tpAmb,
                "verAplic", verAplic(),
                "cStat", cStat,
                "xMotivo", xMotivo,
                "cUF", cUF,
                "dhRecbto", SefazClock.format(agora),
                "resultado", resultado));
        return SoapResponse.ok(xml, cStat, xMotivo, chavesDe(notas));
    }

    private static List<String> chavesDe(List<NotaLida> notas) {
        return notas.stream().flatMap(n -> n.chave().stream()).map(ChaveAcesso::valor).toList();
    }

    private static List<NotaLida> notas(SoapRequest request) {
        NodeList nodes = request.nodes(NFE);
        List<NotaLida> notas = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) {
            Element nfe = (Element) nodes.item(i);
            String id = SecureXml.text(nfe, "*[local-name()='infNFe']/@Id");
            Optional<ChaveAcesso> chave = ChaveAcesso.isValida(id) ? Optional.of(ChaveAcesso.parse(id)) : Optional.empty();
            String tpAmb = SecureXml.text(nfe, "*[local-name()='infNFe']/*[local-name()='ide']/*[local-name()='tpAmb']");
            String digVal = SecureXml.text(nfe, ".//*[local-name()='Signature']//*[local-name()='DigestValue']");
            notas.add(new NotaLida(chave, tpAmb == null || tpAmb.isEmpty() ? TP_AMB_HOMOLOGACAO : tpAmb,
                    digVal == null ? "" : digVal, SecureXml.toString(nfe)));
        }
        return notas;
    }

    private record NotaLida(Optional<ChaveAcesso> chave, String tpAmb, String digVal, String xml) {
    }

    private record Resultado(String protNFe, String cStat, String xMotivo) {
    }
}
