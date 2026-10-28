package io.github.vbaggio.sefaz.testcontainers;

/** Web services da NF-e 4.00 atendidos pela SEFAZ fake. */
public enum SefazService {

    STATUS_SERVICO("NFeStatusServico4"),
    AUTORIZACAO("NFeAutorizacao4"),
    RET_AUTORIZACAO("NFeRetAutorizacao4"),
    CONSULTA_PROTOCOLO("NFeConsultaProtocolo4");

    private final String serviceName;

    SefazService(String serviceName) {
        this.serviceName = serviceName;
    }

    /** Nome do serviço, também o fim do namespace do {@code nfeDadosMsg}. */
    public String serviceName() {
        return serviceName;
    }

    /** Path usado nas URLs geradas. O servidor roteia pelo namespace, então qualquer path funciona. */
    public String path() {
        return "/ws/" + serviceName;
    }

    /** Namespace WSDL da operação. */
    public String wsdlNamespace() {
        return "http://www.portalfiscal.inf.br/nfe/wsdl/" + serviceName;
    }
}
