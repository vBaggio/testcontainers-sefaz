package io.github.vbaggio.sefaz.mock.soap;

import java.util.Locale;
import java.util.Optional;

/** Versões de SOAP aceitas, identificadas pelo content-type da requisição. */
public enum SoapVersion {

    SOAP_11("http://schemas.xmlsoap.org/soap/envelope/", "text/xml"),
    SOAP_12("http://www.w3.org/2003/05/soap-envelope", "application/soap+xml");

    private final String envelopeNamespace;
    private final String mediaType;

    SoapVersion(String envelopeNamespace, String mediaType) {
        this.envelopeNamespace = envelopeNamespace;
        this.mediaType = mediaType;
    }

    public String envelopeNamespace() {
        return envelopeNamespace;
    }

    public String contentType() {
        return mediaType + "; charset=utf-8";
    }

    /** Versão pelo content-type; vazio para qualquer outro tipo (a requisição recebe 415). */
    public static Optional<SoapVersion> fromContentType(String contentType) {
        if (contentType == null) {
            return Optional.empty();
        }
        String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        for (SoapVersion version : values()) {
            if (version.mediaType.equals(mediaType)) {
                return Optional.of(version);
            }
        }
        return Optional.empty();
    }
}
