package io.github.vbaggio.sefaz.testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * SEFAZ fake (NF-e/NFC-e 4.00) num container: web services em HTTPS e HTTP e uma API admin para
 * configurar cenários de falha e fazer asserções.
 *
 * <pre>{@code
 * @Container
 * static SefazContainer sefaz = new SefazContainer();
 *
 * sefaz.getUrl(SefazService.AUTORIZACAO);   // https://localhost:32771/ws/NFeAutorizacao4
 * sefaz.admin().when(Match.numero("123")).then(Scenario.servicePaused());
 * }</pre>
 */
public class SefazContainer extends GenericContainer<SefazContainer> {

    public static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("ghcr.io/vbaggio/sefaz-mock-server");
    /** Tag padrão: a mesma versão desta biblioteca. */
    public static final String DEFAULT_TAG = libraryVersion();

    public static final int HTTPS_PORT = 8443;
    public static final int HTTP_PORT = 8080;
    public static final int ADMIN_PORT = 8081;

    static final String TRUSTSTORE_PASSWORD = "changeit";
    private static final String XSD_DIR = "/sefaz/xsd";
    private static final ObjectMapper JSON = new ObjectMapper();

    private SefazAdmin admin;
    private KeyStore trustStore;

    /** Imagem {@code ghcr.io/vbaggio/sefaz-mock-server} com a tag desta versão da biblioteca. */
    public SefazContainer() {
        this(DEFAULT_IMAGE_NAME.withTag(DEFAULT_TAG));
    }

    public SefazContainer(String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    public SefazContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(DEFAULT_IMAGE_NAME);
        withExposedPorts(HTTPS_PORT, HTTP_PORT, ADMIN_PORT);
        waitingFor(Wait.forHttp("/__admin/health").forPort(ADMIN_PORT));
    }

    /** Cenário aplicado quando nenhuma regra casa. */
    public SefazContainer withDefaultScenario(Scenario scenario) {
        withEnv("SEFAZ_DEFAULT_SCENARIO", scenario.name());
        try {
            withEnv("SEFAZ_DEFAULT_SCENARIO_PARAMS", JSON.writeValueAsString(scenario.params()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return self();
    }

    public SefazContainer withClientAuth(ClientAuth mode) {
        return withEnv("SEFAZ_CLIENT_AUTH", mode.name().toLowerCase(Locale.ROOT));
    }

    /** Copia os XSDs do diretório para o container e liga a validação opcional (ADR-001). */
    public SefazContainer withXsdDirectory(Path dir) {
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Diretório de XSDs não existe: " + dir);
        }
        withCopyFileToContainer(MountableFile.forHostPath(dir), XSD_DIR);
        return withEnv("SEFAZ_XSD_DIR", XSD_DIR);
    }

    @Override
    protected void configure() {
        Set<String> sans = new LinkedHashSet<>();
        sans.add(getHost());
        sans.add(DockerClientFactory.instance().dockerHostIpAddress());
        sans.add("localhost");
        sans.add("127.0.0.1");
        withEnv("SEFAZ_TLS_SANS", String.join(",", sans));
    }

    /** {@code https://host:porta} dos web services. */
    public String getBaseUrl() {
        return "https://" + getHost() + ":" + getMappedPort(HTTPS_PORT);
    }

    /** {@code http://host:porta} dos web services, para clientes que aceitam HTTP. */
    public String getHttpBaseUrl() {
        return "http://" + getHost() + ":" + getMappedPort(HTTP_PORT);
    }

    /** {@code http://host:porta} da API admin. */
    public String getAdminUrl() {
        return "http://" + getHost() + ":" + getMappedPort(ADMIN_PORT);
    }

    /** URL HTTPS completa de um serviço. */
    public String getUrl(SefazService service) {
        return getBaseUrl() + service.path();
    }

    /** URL HTTP completa de um serviço. */
    public String getHttpUrl(SefazService service) {
        return getHttpBaseUrl() + service.path();
    }

    /** Truststore com o certificado autoassinado gerado pelo container nesta execução. */
    public synchronized KeyStore getTrustStore() {
        if (trustStore == null) {
            try (InputStream in = new ByteArrayInputStream(admin().trustStorePkcs12())) {
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                keyStore.load(in, TRUSTSTORE_PASSWORD.toCharArray());
                trustStore = keyStore;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("Truststore inválido", e);
            }
        }
        return trustStore;
    }

    /** Grava o truststore em PKCS12 em {@code dir/sefaz-truststore.p12} e devolve o caminho. */
    public Path writeTrustStore(Path dir, char[] password) {
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve("sefaz-truststore.p12");
            try (OutputStream out = Files.newOutputStream(file)) {
                getTrustStore().store(out, password);
            }
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao gravar truststore", e);
        }
    }

    /**
     * Gera {@code dir/WebServicesNfe.ini} no formato da Java_NFe, com as URLs HTTPS de homologação e
     * produção das UFs pedidas apontando para o container. Use com
     * {@code ConfiguracoesNfe.setArquivoWebService}.
     */
    public Path writeJavaNfeWebServiceFile(Path dir, Estado... ufs) {
        return writeJavaNfeWebServiceFile(dir, getBaseUrl(), ufs);
    }

    /** Como {@link #writeJavaNfeWebServiceFile(Path, Estado...)}, com outra URL base, por exemplo {@link #getHttpBaseUrl()}. */
    public Path writeJavaNfeWebServiceFile(Path dir, String baseUrl, Estado... ufs) {
        return JavaNfeWebServiceFile.write(dir, baseUrl, ufs);
    }

    /** Cliente da API admin. */
    public synchronized SefazAdmin admin() {
        if (admin == null) {
            admin = new SefazAdmin(getAdminUrl());
        }
        return admin;
    }

    @Override
    public void stop() {
        super.stop();
        synchronized (this) {
            admin = null;
            trustStore = null;
        }
    }

    private static String libraryVersion() {
        try (InputStream in = SefazContainer.class.getResourceAsStream("/META-INF/testcontainers-sefaz.properties")) {
            Properties properties = new Properties();
            if (in != null) {
                properties.load(in);
            }
            return properties.getProperty("version", "latest");
        } catch (IOException e) {
            return "latest";
        }
    }
}
