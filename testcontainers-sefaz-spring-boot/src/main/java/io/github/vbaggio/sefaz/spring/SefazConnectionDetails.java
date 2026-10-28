package io.github.vbaggio.sefaz.spring;

import io.github.vbaggio.sefaz.testcontainers.SefazService;
import java.security.KeyStore;
import org.springframework.boot.autoconfigure.service.connection.ConnectionDetails;

/**
 * Como a aplicação chega à SEFAZ fake. Só tem efeito se a aplicação ler este bean; o caminho mais
 * simples é {@link SefazTest#urlProperty()}.
 */
public interface SefazConnectionDetails extends ConnectionDetails {

    /** {@code https://host:porta} dos web services. */
    String baseUrl();

    /** {@code http://host:porta} dos web services. */
    String httpBaseUrl();

    /** URL HTTPS completa de um serviço. */
    String url(SefazService service);

    /** Truststore com o certificado autoassinado do container. */
    KeyStore trustStore();
}
