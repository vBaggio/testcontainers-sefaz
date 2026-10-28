package io.github.vbaggio.sefaz.mock.soap;

import io.github.vbaggio.sefaz.mock.xml.SecureXml;
import io.github.vbaggio.sefaz.mock.xml.Template;
import io.github.vbaggio.sefaz.mock.xml.XmlException;
import java.io.InputStream;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Leitura e escrita de envelopes SOAP sem JAX-WS (ADR-002). */
public final class SoapEnvelopes {

    private SoapEnvelopes() {
    }

    /** Envelope mal formado ou sem {@code nfeDadosMsg}. */
    public static final class InvalidEnvelopeException extends Exception {
        public InvalidEnvelopeException(String message) {
            super(message);
        }
    }

    public static SoapRequest read(SoapVersion version, String path, InputStream body, String clientCertificate)
            throws InvalidEnvelopeException {
        Document document;
        try {
            document = SecureXml.parse(body);
        } catch (XmlException e) {
            throw new InvalidEnvelopeException(e.getMessage());
        }
        Element envelope = document.getDocumentElement();
        if (!"Envelope".equals(envelope.getLocalName())
                || !version.envelopeNamespace().equals(envelope.getNamespaceURI())) {
            throw new InvalidEnvelopeException(
                    "Envelope SOAP esperado em " + version.envelopeNamespace() + " para o content-type recebido");
        }
        Element body1 = firstChild(envelope, "Body");
        if (body1 == null) {
            throw new InvalidEnvelopeException("Envelope sem Body");
        }
        Element dadosMsg = firstChild(body1, "nfeDadosMsg");
        if (dadosMsg == null || dadosMsg.getNamespaceURI() == null) {
            throw new InvalidEnvelopeException("Body sem nfeDadosMsg com namespace do serviço");
        }
        Element content = firstElement(dadosMsg);
        if (content == null) {
            throw new InvalidEnvelopeException("nfeDadosMsg vazio");
        }
        return new SoapRequest(version, path, dadosMsg.getNamespaceURI(), content, clientCertificate);
    }

    public static String result(SoapVersion version, String wsdlNamespace, String resultXml) {
        return Template.load(envelopeTemplate(version)).render(Map.of(
                "body", Template.load("nfeResultMsg").render(Map.of("ns", wsdlNamespace, "content", resultXml))));
    }

    public static String fault(SoapVersion version, String reason) {
        String template = version == SoapVersion.SOAP_12 ? "fault-soap12" : "fault-soap11";
        return Template.load(envelopeTemplate(version))
                .render(Map.of("body", Template.load(template).render(Map.of("reason", reason))));
    }

    private static String envelopeTemplate(SoapVersion version) {
        return version == SoapVersion.SOAP_12 ? "envelope-soap12" : "envelope-soap11";
    }

    private static Element firstChild(Element parent, String localName) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && localName.equals(e.getLocalName())) {
                return e;
            }
        }
        return null;
    }

    private static Element firstElement(Element parent) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) {
                return e;
            }
        }
        return null;
    }
}
