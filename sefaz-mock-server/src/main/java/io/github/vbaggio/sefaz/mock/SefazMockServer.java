package io.github.vbaggio.sefaz.mock;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsServer;
import io.github.vbaggio.sefaz.mock.admin.AdminHttpHandler;
import io.github.vbaggio.sefaz.mock.admin.RequestHistory;
import io.github.vbaggio.sefaz.mock.handler.HandlerEnvironment;
import io.github.vbaggio.sefaz.mock.handler.HandlerRegistry;
import io.github.vbaggio.sefaz.mock.nfe.SefazClock;
import io.github.vbaggio.sefaz.mock.scenario.ScenarioRegistry;
import io.github.vbaggio.sefaz.mock.store.InMemoryNotaStore;
import io.github.vbaggio.sefaz.mock.store.NotaStore;
import io.github.vbaggio.sefaz.mock.tls.TlsMaterial;
import io.github.vbaggio.sefaz.mock.tls.TlsSupport;
import io.github.vbaggio.sefaz.mock.xsd.XsdValidator;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/** SEFAZ fake: web services SOAP em HTTPS e HTTP e a API admin numa porta separada. */
public final class SefazMockServer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(SefazMockServer.class.getName());

    private final HttpsServer https;
    private final HttpServer http;
    private final HttpServer admin;
    private final ExecutorService executor;
    private final TlsMaterial tls;
    private final NotaStore store;
    private final ScenarioRegistry scenarios;
    private final RequestHistory history;

    private SefazMockServer(ServerConfig config, SefazClock clock) throws IOException {
        String version = version();
        this.tls = TlsMaterial.generate(config.tlsSans());
        this.store = new InMemoryNotaStore();
        this.scenarios = new ScenarioRegistry(config.defaultScenario());
        this.history = new RequestHistory(config.historyLimit());
        HandlerRegistry handlers = HandlerRegistry.load(new HandlerEnvironment(clock, verAplic(version)));
        XsdValidator xsd = config.xsdDirectory() == null ? XsdValidator.disabled() : new XsdValidator(config.xsdDirectory());
        SoapHttpHandler soap = new SoapHttpHandler(handlers, scenarios, store, history, xsd, clock);

        // Uma virtual thread por requisição: cenários de atraso dormem sem prender threads de plataforma.
        this.executor = Executors.newVirtualThreadPerTaskExecutor();

        this.https = HttpsServer.create(new InetSocketAddress(config.httpsPort()), 0);
        this.https.setHttpsConfigurator(TlsSupport.configurator(tls, config.clientAuth()));
        this.https.createContext("/", soap);
        this.https.setExecutor(executor);

        this.http = HttpServer.create(new InetSocketAddress(config.httpPort()), 0);
        this.http.createContext("/", soap);
        this.http.setExecutor(executor);

        this.admin = HttpServer.create(new InetSocketAddress(config.adminPort()), 0);
        this.admin.createContext(AdminHttpHandler.PREFIX, new AdminHttpHandler(scenarios, store, history, tls, version));
        this.admin.setExecutor(executor);
    }

    public static SefazMockServer start(ServerConfig config) {
        return start(config, SefazClock.system());
    }

    public static SefazMockServer start(ServerConfig config, SefazClock clock) {
        try {
            SefazMockServer server = new SefazMockServer(config, clock);
            server.https.start();
            server.http.start();
            server.admin.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao abrir as portas do servidor", e);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT %4$s %5$s%6$s%n");
        ServerConfig config = ServerConfig.fromEnvironment(System::getenv);
        SefazMockServer server = start(config);
        LOG.info(() -> String.format("sefaz-mock-server %s: HTTPS %d, HTTP %d, admin %d, cenário padrão %s, client auth %s",
                version(), server.httpsPort(), server.httpPort(), server.adminPort(),
                config.defaultScenario().scenario(), config.clientAuth()));
        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            stopped.countDown();
        }, "shutdown"));
        stopped.await();
    }

    public int httpsPort() {
        return https.getAddress().getPort();
    }

    public int httpPort() {
        return http.getAddress().getPort();
    }

    public int adminPort() {
        return admin.getAddress().getPort();
    }

    public TlsMaterial tls() {
        return tls;
    }

    public NotaStore store() {
        return store;
    }

    public ScenarioRegistry scenarios() {
        return scenarios;
    }

    public RequestHistory history() {
        return history;
    }

    @Override
    public void close() {
        https.stop(0);
        http.stop(0);
        admin.stop(0);
        executor.shutdownNow();
    }

    static String version() {
        try (InputStream in = SefazMockServer.class.getResourceAsStream("/META-INF/sefaz-mock-server.properties")) {
            Properties properties = new Properties();
            if (in != null) {
                properties.load(in);
            }
            return properties.getProperty("version", "dev");
        } catch (IOException e) {
            return "dev";
        }
    }

    /** {@code verAplic} tem no máximo 20 caracteres no leiaute. */
    static String verAplic(String version) {
        String value = "SEFAZ-MOCK_" + version;
        return value.length() > 20 ? value.substring(0, 20) : value;
    }
}
