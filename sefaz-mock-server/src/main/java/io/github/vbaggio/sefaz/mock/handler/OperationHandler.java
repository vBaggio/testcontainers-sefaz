package io.github.vbaggio.sefaz.mock.handler;

import io.github.vbaggio.sefaz.mock.nfe.ChaveAcesso;
import io.github.vbaggio.sefaz.mock.scenario.ScenarioContext;
import io.github.vbaggio.sefaz.mock.soap.SoapRequest;
import io.github.vbaggio.sefaz.mock.soap.SoapResponse;
import io.github.vbaggio.sefaz.mock.store.NotaStore;
import java.util.List;
import java.util.Locale;

/**
 * Uma operação de web service da SEFAZ. Implementações são carregadas via {@link java.util.ServiceLoader}:
 * um serviço novo é uma classe nova mais uma linha em
 * {@code META-INF/services/io.github.vbaggio.sefaz.mock.handler.OperationHandler}.
 *
 * <p>Atrasos, conexões fechadas e HTTP 503 são aplicados pelo servidor antes ou depois do handler;
 * o handler só precisa tratar os cenários que mudam o conteúdo da resposta.
 */
public interface OperationHandler {

    /** Namespace do elemento {@code nfeDadosMsg} que identifica a operação (ADR-003). */
    String wsdlNamespace();

    SoapResponse handle(SoapRequest request, ScenarioContext scenario, NotaStore store);

    /** Nome da operação no histórico e nas regras; por padrão o nome do serviço em maiúsculas. */
    default String operation() {
        String ns = wsdlNamespace();
        return ns.substring(ns.lastIndexOf('/') + 1).toUpperCase(Locale.ROOT);
    }

    /** Chaves de acesso da requisição, usadas para casar regras de cenário por nota. */
    default List<ChaveAcesso> chaves(SoapRequest request, NotaStore store) {
        return List.of();
    }

    /** Arquivo XSD que valida o conteúdo de {@code nfeDadosMsg} quando {@code SEFAZ_XSD_DIR} existe. */
    default String xsdFileName() {
        return null;
    }

    /** {@code cStat} devolvido quando a validação por XSD falha. */
    default String schemaFailureCStat() {
        return io.github.vbaggio.sefaz.mock.nfe.Motivo.FALHA_SCHEMA;
    }

    /** Chamado uma vez, antes da primeira requisição. */
    default void init(HandlerEnvironment environment) {
    }
}
