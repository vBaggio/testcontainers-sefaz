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
import io.github.vbaggio.sefaz.mock.xml.Template;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * NFeConsultaProtocolo4 / {@code nfeConsultaNF}. É o que permite ao emissor descobrir, depois de
 * um timeout, se a nota foi autorizada.
 */
public final class ConsultaProtocoloHandler extends NfeHandler {

    @Override
    public String wsdlNamespace() {
        return Operations.WSDL_BASE + "NFeConsultaProtocolo4";
    }

    @Override
    public String operation() {
        return Operations.CONSULTA_PROTOCOLO;
    }

    @Override
    public String xsdFileName() {
        return "consSitNFe_v4.00.xsd";
    }

    @Override
    public List<ChaveAcesso> chaves(SoapRequest request, NotaStore store) {
        String chNFe = request.text(child("chNFe"));
        return ChaveAcesso.isValida(chNFe) ? List.of(ChaveAcesso.parse(chNFe)) : List.of();
    }

    @Override
    public SoapResponse handle(SoapRequest request, ScenarioContext scenario, NotaStore store) {
        String chNFe = Optional.ofNullable(request.text(child("chNFe"))).orElse("");
        String tpAmb = tpAmb(request, child("tpAmb"));
        String cUF = chNFe.length() >= 2 ? chNFe.substring(0, 2) : "";

        if (scenario.is(Scenario.REJECT)) {
            String cStat = scenario.cStat(Motivo.FALHA_SCHEMA);
            return resposta(tpAmb, cUF, chNFe, cStat, scenario.xMotivo(Motivo.texto(cStat)), "");
        }
        Optional<NotaRecord> nota = store.find(chNFe.toUpperCase(java.util.Locale.ROOT)).filter(NotaRecord::naBase);
        if (nota.isEmpty()) {
            return resposta(tpAmb, cUF, chNFe, Motivo.NAO_CONSTA_NA_BASE, Motivo.texto(Motivo.NAO_CONSTA_NA_BASE), "");
        }
        NotaRecord n = nota.get();
        return resposta(tpAmb, cUF, chNFe, n.cStat(), n.xMotivo(), Protocolos.deNota(n, verAplic()));
    }

    private SoapResponse resposta(String tpAmb, String cUF, String chNFe, String cStat, String xMotivo,
            String protocolo) {
        String xml = Template.load("retConsSitNFe").render(Map.of(
                "tpAmb", tpAmb,
                "verAplic", verAplic(),
                "cStat", cStat,
                "xMotivo", xMotivo,
                "cUF", cUF,
                "dhRecbto", SefazClock.format(clock().now()),
                "chNFe", chNFe,
                "protocolo", protocolo));
        return SoapResponse.ok(xml, cStat, xMotivo, chNFe.isEmpty() ? List.of() : List.of(chNFe));
    }
}
