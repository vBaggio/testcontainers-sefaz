package io.github.vbaggio.sefaz.testcontainers;

import static io.github.vbaggio.sefaz.testcontainers.Soap.tag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Opções do container que exigem um container próprio: cenário padrão, client auth e XSD. */
class SefazContainerConfigTest {

    @Test
    void cenarioPadraoDoContainer() throws Exception {
        try (SefazContainer sefaz = new SefazContainer(SefazContainerTest.IMAGE)
                .withDefaultScenario(Scenario.servicePaused())) {
            sefaz.start();

            String body = Soap.post(HttpClient.newHttpClient(), sefaz.getHttpUrl(SefazService.STATUS_SERVICO),
                    Soap.status(), Duration.ofSeconds(5)).body();

            assertThat(tag(body, "cStat")).isEqualTo("108");
            assertThat(sefaz.admin().requests()).singleElement().extracting(RecordedRequest::rule).isEqualTo("default");
        }
    }

    @Test
    void clientAuthNeedExigeCertificadoDeCliente(@TempDir Path dir) throws Exception {
        try (SefazContainer sefaz = new SefazContainer(SefazContainerTest.IMAGE).withClientAuth(ClientAuth.NEED)) {
            sefaz.start();
            String url = sefaz.getUrl(SefazService.STATUS_SERVICO);

            assertThatThrownBy(() -> Soap.post(Soap.client(sefaz.getTrustStore()), url, Soap.status(), Duration.ofSeconds(5)))
                    .isInstanceOf(IOException.class);

            HttpClient comCertificado = clientWithCertificate(sefaz.getTrustStore(), certificadoDeCliente(dir));
            assertThat(tag(Soap.post(comCertificado, url, Soap.status(), Duration.ofSeconds(5)).body(), "cStat"))
                    .isEqualTo("107");
            assertThat(sefaz.admin().requests().get(sefaz.admin().requests().size() - 1).clientCertificate())
                    .contains("CN=Emissor Ficticio");
        }
    }

    @Test
    void clientAuthNoneAceitaClienteSemCertificado() throws Exception {
        try (SefazContainer sefaz = new SefazContainer(SefazContainerTest.IMAGE).withClientAuth(ClientAuth.NONE)) {
            sefaz.start();

            String body = Soap.post(Soap.client(sefaz.getTrustStore()), sefaz.getUrl(SefazService.STATUS_SERVICO),
                    Soap.status(), Duration.ofSeconds(5)).body();

            assertThat(tag(body, "cStat")).isEqualTo("107");
        }
    }

    @Test
    void validacaoPorXsd() throws Exception {
        Path xsd = Path.of(SefazContainerConfigTest.class.getResource("/xsd").toURI());
        try (SefazContainer sefaz = new SefazContainer(SefazContainerTest.IMAGE).withXsdDirectory(xsd)) {
            sefaz.start();
            HttpClient http = HttpClient.newHttpClient();
            String url = sefaz.getHttpUrl(SefazService.CONSULTA_PROTOCOLO);

            assertThat(tag(Soap.post(http, url, Soap.consulta("123"), Duration.ofSeconds(5)).body(), "cStat")).isEqualTo("215");
            assertThat(tag(Soap.post(http, url, Soap.consulta(Soap.chave(1)), Duration.ofSeconds(5)).body(), "cStat"))
                    .isEqualTo("217");
        }
    }

    /** Gera um certificado de cliente com o keytool do JDK. */
    private static Path certificadoDeCliente(Path dir) throws Exception {
        Path file = dir.resolve("cliente.p12");
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        Process process = new ProcessBuilder(List.of(keytool, "-genkeypair", "-alias", "cliente", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "2", "-dname", "CN=Emissor Ficticio, O=Teste", "-storetype", "PKCS12",
                "-keystore", file.toString(), "-storepass", "changeit", "-keypass", "changeit"))
                .redirectErrorStream(true)
                .start();
        assertThat(process.waitFor()).as(new String(process.getInputStream().readAllBytes())).isZero();
        return file;
    }

    private static HttpClient clientWithCertificate(KeyStore trustStore, Path p12) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(p12)) {
            keyStore.load(in, "changeit".toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, "changeit".toCharArray());
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return HttpClient.newBuilder().sslContext(ssl).build();
    }
}
