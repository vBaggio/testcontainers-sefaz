package io.github.vbaggio.sefaz.mock.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Leitura de XML com proteção contra XXE e consultas XPath que ignoram namespace.
 *
 * <p>O servidor nunca confia no XML recebido: DTDs são recusados, entidades externas não são
 * resolvidas e o processamento seguro do JDK fica ligado.
 */
public final class SecureXml {

    private static final DocumentBuilderFactory FACTORY = newFactory();
    private static final ThreadLocal<XPath> XPATH =
            ThreadLocal.withInitial(() -> XPathFactory.newInstance().newXPath());

    private SecureXml() {
    }

    private static DocumentBuilderFactory newFactory() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory;
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Parser XML do JDK não suporta processamento seguro", e);
        }
    }

    /** Lê um documento; qualquer DTD ou entidade externa faz a leitura falhar. */
    public static Document parse(InputStream in) throws XmlException {
        try {
            DocumentBuilder builder = FACTORY.newDocumentBuilder();
            builder.setErrorHandler(null);
            return builder.parse(in);
        } catch (SAXException | IOException | ParserConfigurationException e) {
            throw new XmlException("XML inválido: " + e.getMessage(), e);
        }
    }

    /** Texto do primeiro nó que casar com a expressão, ou {@code null}. */
    public static String text(Node context, String expression) {
        Node node = node(context, expression);
        if (node == null) {
            return null;
        }
        String value = node.getTextContent();
        return value == null ? null : value.trim();
    }

    public static Node node(Node context, String expression) {
        try {
            return (Node) XPATH.get().evaluate(expression, context, XPathConstants.NODE);
        } catch (XPathExpressionException e) {
            throw new IllegalArgumentException("XPath inválido: " + expression, e);
        }
    }

    public static NodeList nodes(Node context, String expression) {
        try {
            return (NodeList) XPATH.get().evaluate(expression, context, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            throw new IllegalArgumentException("XPath inválido: " + expression, e);
        }
    }

    /** Serializa um nó sem declaração XML, como recebido. */
    public static String toString(Node node) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter out = new StringWriter();
            transformer.transform(new DOMSource(node), new StreamResult(out));
            return out.toString();
        } catch (TransformerException e) {
            throw new IllegalStateException("Falha ao serializar XML", e);
        }
    }

    /** Escapa texto para uso em conteúdo de elemento ou valor de atributo. */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> {
                    // Caracteres de controle não são permitidos em XML 1.0.
                    if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                        continue;
                    }
                    out.append(c);
                }
            }
        }
        return out.toString();
    }
}
