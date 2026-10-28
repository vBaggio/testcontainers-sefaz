package io.github.vbaggio.sefaz.mock.soap;

import io.github.vbaggio.sefaz.mock.xml.SecureXml;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Requisição SOAP já lida.
 *
 * @param wsdlNamespace namespace do elemento {@code nfeDadosMsg}, que identifica a operação
 * @param content primeiro elemento dentro de {@code nfeDadosMsg} (por exemplo {@code enviNFe})
 * @param clientCertificate subject do certificado de cliente, ou {@code null}
 */
public record SoapRequest(
        SoapVersion version,
        String path,
        String wsdlNamespace,
        Element content,
        String clientCertificate) {

    /** Texto do primeiro nó que casar com o XPath, relativo a {@link #content()}. */
    public String text(String xpath) {
        return SecureXml.text(content, xpath);
    }

    public NodeList nodes(String xpath) {
        return SecureXml.nodes(content, xpath);
    }
}
