package io.github.vbaggio.sefaz.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.vbaggio.sefaz.mock.admin.Json;
import io.github.vbaggio.sefaz.mock.tls.ClientAuth;
import io.github.vbaggio.sefaz.mock.tls.TlsMaterial;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.Test;

class TlsTest {

    @Test
    void httpsComCertificadoAutoassinadoESansInformados() throws Exception {
        try (TestServer sefaz = new TestServer(ServerConfig.ephemeral()
                .withClientAuth(ClientAuth.NONE)
                .withPorts(0, 0, 0))) {
            HttpResponse<String> response = post(sefaz, sslContext(sefaz.server.tls(), null));

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(TestServer.tag(response.body(), "cStat")).isEqualTo("107");
        }
    }

    @Test
    void certificadoIncluiSansExtras() throws Exception {
        TlsMaterial tls = TlsMaterial.generate(List.of("172.17.0.1", "docker-host"));

        assertThat(tls.certificate().getSubjectAlternativeNames())
                .extracting(san -> san.get(1).toString())
                .contains("localhost", "127.0.0.1", "172.17.0.1", "docker-host");
    }

    @Test
    void clientAuthNeedRecusaClienteSemCertificadoEAceitaQualquerCertificado() throws Exception {
        try (TestServer sefaz = new TestServer(ServerConfig.ephemeral().withClientAuth(ClientAuth.NEED))) {
            assertThatThrownBy(() -> post(sefaz, sslContext(sefaz.server.tls(), null))).isInstanceOf(IOException.class);

            TlsMaterial cliente = TlsMaterial.generate(List.of());
            HttpResponse<String> response = post(sefaz, sslContext(sefaz.server.tls(), cliente));
            assertThat(TestServer.tag(response.body(), "cStat")).isEqualTo("107");

            JsonNode history = Json.MAPPER.readTree(sefaz.admin("GET", "/requests", null).body());
            assertThat(history.get(history.size() - 1).get("clientCertificate").asText())
                    .contains("CN=sefaz-mock-server");
        }
    }

    private static HttpResponse<String> post(TestServer sefaz, SSLContext ssl) throws Exception {
        HttpClient client = HttpClient.newBuilder().sslContext(ssl).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://localhost:" + sefaz.server.httpsPort() + "/ws"))
                .header("Content-Type", TestServer.SOAP12)
                .POST(HttpRequest.BodyPublishers.ofString(Fixtures.resource("requests/status.xml")))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static SSLContext sslContext(TlsMaterial server, TlsMaterial client) throws Exception {
        KeyStore trust = KeyStore.getInstance("PKCS12");
        trust.load(new ByteArrayInputStream(server.trustStorePkcs12()), TlsMaterial.TRUSTSTORE_PASSWORD.toCharArray());
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trust);
        KeyManagerFactory kmf = null;
        if (client != null) {
            // SunX509 não exige EKU clientAuth; o servidor aceita qualquer certificado.
            kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(client.keyStore(), client.keyPassword());
        }
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf == null ? null : kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return context;
    }
}
