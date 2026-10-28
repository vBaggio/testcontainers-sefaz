package io.github.vbaggio.sefaz.mock.handler;

import io.github.vbaggio.sefaz.mock.nfe.SefazClock;
import io.github.vbaggio.sefaz.mock.soap.SoapRequest;

/** Base dos handlers da NF-e 4.00: relógio, {@code verAplic} e leitura de campos comuns. */
public abstract class NfeHandler implements OperationHandler {

    protected static final String TP_AMB_HOMOLOGACAO = "2";

    private HandlerEnvironment environment = new HandlerEnvironment(SefazClock.system(), "SEFAZ-MOCK");

    @Override
    public void init(HandlerEnvironment environment) {
        this.environment = environment;
    }

    protected SefazClock clock() {
        return environment.clock();
    }

    protected String verAplic() {
        return environment.verAplic();
    }

    /** {@code tpAmb} da resposta repete o da requisição. */
    protected static String tpAmb(SoapRequest request, String xpath) {
        String value = request.text(xpath);
        return value == null || value.isEmpty() ? TP_AMB_HOMOLOGACAO : value;
    }

    /** XPath de um filho direto pelo nome local, sem depender do prefixo de namespace. */
    protected static String child(String localName) {
        return "*[local-name()='" + localName + "']";
    }
}
