package io.github.vbaggio.sefaz.mock.handler;

import io.github.vbaggio.sefaz.mock.nfe.Motivo;
import io.github.vbaggio.sefaz.mock.nfe.SefazClock;
import io.github.vbaggio.sefaz.mock.scenario.Scenario;
import io.github.vbaggio.sefaz.mock.scenario.ScenarioContext;
import io.github.vbaggio.sefaz.mock.soap.SoapRequest;
import io.github.vbaggio.sefaz.mock.soap.SoapResponse;
import io.github.vbaggio.sefaz.mock.store.NotaStore;
import io.github.vbaggio.sefaz.mock.xml.Template;
import java.util.List;
import java.util.Map;

/** NFeStatusServico4 / {@code nfeStatusServicoNF}. */
public final class StatusServicoHandler extends NfeHandler {

    @Override
    public String wsdlNamespace() {
        return Operations.WSDL_BASE + "NFeStatusServico4";
    }

    @Override
    public String operation() {
        return Operations.STATUS_SERVICO;
    }

    @Override
    public String xsdFileName() {
        return "consStatServ_v4.00.xsd";
    }

    @Override
    public SoapResponse handle(SoapRequest request, ScenarioContext scenario, NotaStore store) {
        String cStat;
        String xMotivo;
        if (scenario.is(Scenario.SERVICE_PAUSED)) {
            cStat = Motivo.SERVICO_PARALISADO;
            xMotivo = Motivo.texto(cStat);
        } else if (scenario.is(Scenario.REJECT)) {
            cStat = scenario.cStat(Motivo.FALHA_SCHEMA);
            xMotivo = scenario.xMotivo(Motivo.texto(cStat));
        } else {
            cStat = Motivo.SERVICO_EM_OPERACAO;
            xMotivo = Motivo.texto(cStat);
        }
        String cUF = request.text(child("cUF"));
        String xml = Template.load("retConsStatServ").render(Map.of(
                "tpAmb", tpAmb(request, child("tpAmb")),
                "verAplic", verAplic(),
                "cStat", cStat,
                "xMotivo", xMotivo,
                "cUF", cUF == null ? "" : cUF,
                "dhRecbto", SefazClock.format(clock().now())));
        return SoapResponse.ok(xml, cStat, xMotivo, List.of());
    }
}
