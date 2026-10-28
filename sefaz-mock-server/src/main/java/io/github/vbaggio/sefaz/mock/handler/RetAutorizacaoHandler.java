package io.github.vbaggio.sefaz.mock.handler;

import io.github.vbaggio.sefaz.mock.nfe.ChaveAcesso;
import io.github.vbaggio.sefaz.mock.nfe.Motivo;
import io.github.vbaggio.sefaz.mock.nfe.SefazClock;
import io.github.vbaggio.sefaz.mock.scenario.Scenario;
import io.github.vbaggio.sefaz.mock.scenario.ScenarioContext;
import io.github.vbaggio.sefaz.mock.soap.SoapRequest;
import io.github.vbaggio.sefaz.mock.soap.SoapResponse;
import io.github.vbaggio.sefaz.mock.store.NotaStore;
import io.github.vbaggio.sefaz.mock.store.Recibo;
import io.github.vbaggio.sefaz.mock.xml.Template;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** NFeRetAutorizacao4 / {@code nfeRetAutorizacaoLote}: consulta do recibo no modo assíncrono. */
public final class RetAutorizacaoHandler extends NfeHandler {

    @Override
    public String wsdlNamespace() {
        return Operations.WSDL_BASE + "NFeRetAutorizacao4";
    }

    @Override
    public String operation() {
        return Operations.RET_AUTORIZACAO;
    }

    @Override
    public String xsdFileName() {
        return "consReciNFe_v4.00.xsd";
    }

    @Override
    public List<ChaveAcesso> chaves(SoapRequest request, NotaStore store) {
        return store.findRecibo(request.text(child("nRec")))
                .map(r -> r.chaves().stream().map(ChaveAcesso::parse).toList())
                .orElse(List.of());
    }

    @Override
    public SoapResponse handle(SoapRequest request, ScenarioContext scenario, NotaStore store) {
        String nRec = Optional.ofNullable(request.text(child("nRec"))).orElse("");
        String tpAmb = tpAmb(request, child("tpAmb"));
        Optional<Recibo> recibo = store.findRecibo(nRec);
        String cUF = recibo.map(Recibo::cUF).orElse(nRec.length() >= 2 ? nRec.substring(0, 2) : "");
        List<String> chaves = recibo.map(Recibo::chaves).orElse(List.of());

        if (scenario.is(Scenario.REJECT)) {
            String cStat = scenario.cStat(Motivo.FALHA_SCHEMA);
            return resposta(tpAmb, nRec, cUF, cStat, scenario.xMotivo(Motivo.texto(cStat)), "", chaves);
        }
        if (recibo.isEmpty()) {
            return resposta(tpAmb, nRec, cUF, Motivo.LOTE_NAO_LOCALIZADO, Motivo.texto(Motivo.LOTE_NAO_LOCALIZADO), "",
                    chaves);
        }
        Recibo r = recibo.get();
        if (scenario.is(Scenario.PROCESSING) && r.consultasEmProcessamento() < scenario.attempts()) {
            r.registraConsultaEmProcessamento();
            return resposta(tpAmb, nRec, cUF, Motivo.LOTE_EM_PROCESSAMENTO, Motivo.texto(Motivo.LOTE_EM_PROCESSAMENTO),
                    "", chaves);
        }
        return resposta(tpAmb, nRec, cUF, Motivo.LOTE_PROCESSADO, Motivo.texto(Motivo.LOTE_PROCESSADO),
                String.join("", r.protocolosXml()), chaves);
    }

    private SoapResponse resposta(String tpAmb, String nRec, String cUF, String cStat, String xMotivo,
            String protocolos, List<String> chaves) {
        String xml = Template.load("retConsReciNFe").render(Map.of(
                "tpAmb", tpAmb,
                "verAplic", verAplic(),
                "nRec", nRec,
                "cStat", cStat,
                "xMotivo", xMotivo,
                "cUF", cUF,
                "dhRecbto", SefazClock.format(clock().now()),
                "protocolos", protocolos));
        return SoapResponse.ok(xml, cStat, xMotivo, chaves);
    }
}
