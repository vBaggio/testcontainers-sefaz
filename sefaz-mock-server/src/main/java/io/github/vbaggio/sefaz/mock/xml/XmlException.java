package io.github.vbaggio.sefaz.mock.xml;

/** XML recebido que não pôde ser lido. */
public class XmlException extends Exception {

    public XmlException(String message, Throwable cause) {
        super(message, cause);
    }
}
