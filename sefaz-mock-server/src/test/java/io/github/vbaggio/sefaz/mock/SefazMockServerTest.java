package io.github.vbaggio.sefaz.mock;

import static io.github.vbaggio.sefaz.mock.TestServer.tag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.vbaggio.sefaz.mock.admin.Json;
import io.github.vbaggio.sefaz.mock.scenario.Scenario;
import io.github.vbaggio.sefaz.mock.scenario.ScenarioContext;
import java.io.ByteArrayInputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SefazMockServerTest {

    private TestServer sefaz;

    @BeforeEach
    void start() {
        sefaz = new TestServer();
    }

    @AfterEach
    void stop() {
        sefaz.close();
    }

    @Test
    void recusaContentTypeQueNaoESoap() {
        HttpResponse<String> response = sefaz.soap("/ws/NFeStatusServico4", "application/json",
                Fixtures.resource("requests/status.xml"), Duration.ofSeconds(5));

        assertThat(response.statusCode()).isEqualTo(415);
    }

    @Test
    void roteiaPeloNamespaceEmQualquerPath() {
        HttpResponse<String> response = sefaz.soap("/qualquer/coisa", Fixtures.resource("requests/status.xml"));

        assertThat(tag(response.body(), "cStat")).isEqualTo("107");
    }

    @Test
    void envelopeDeVersaoDiferenteDoContentTypeDevolveFault() {
        HttpResponse<String> response = sefaz.soap("/ws", "text/xml", Fixtures.resource("requests/status.xml"),
                Duration.ofSeconds(5));

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).contains("faultstring");
    }

    @Test
    void timeoutAfterAuthorizeGravaANotaAntesDeSegurarAResposta() {
        rule("""
                {"match": {"numero": "123"}, "scenario": "TIMEOUT_AFTER_AUTHORIZE", "params": {"delayMs": 2000}, "times": 1}
                """);

        assertThatThrownBy(() -> sefaz.soap("/ws", TestServer.SOAP12,
                        Fixtures.autorizacao(Fixtures.CHAVE_123, true, ""), Duration.ofMillis(500)))
                .isInstanceOf(UncheckedIOException.class)
                .hasCauseInstanceOf(HttpTimeoutException.class);

        assertThat(tag(consulta(Fixtures.CHAVE_123).body(), "cStat")).isEqualTo("100");
        JsonNode history = json(sefaz.admin("GET", "/requests?operation=AUTORIZACAO", null));
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("scenario").asText()).isEqualTo("TIMEOUT_AFTER_AUTHORIZE");
        assertThat(history.get(0).get("rule").asText()).isEqualTo("rule-1");
        assertThat(history.get(0).get("delivery").asText()).isEqualTo("DELAYED");
        assertThat(history.get(0).get("cStat").asText()).isEqualTo("100");
    }

    @Test
    void timeoutBeforeAuthorizeNaoGravaNada() {
        rule("""
                {"match": {"operation": "AUTORIZACAO"}, "scenario": "TIMEOUT_BEFORE_AUTHORIZE", "params": {"delayMs": 300}}
                """);

        HttpResponse<String> response = autoriza(Fixtures.CHAVE_123);

        assertThat(response.statusCode()).isEqualTo(504);
        assertThat(tag(consulta(Fixtures.CHAVE_123).body(), "cStat")).isEqualTo("217");
    }

    @Test
    void connectionResetAfterAuthorizeFechaOSocketDepoisDeGravar() {
        rule("""
                {"match": {"chave": "%s"}, "scenario": "CONNECTION_RESET_AFTER_AUTHORIZE", "times": 1}
                """.formatted(Fixtures.CHAVE_123));

        assertThatThrownBy(() -> autoriza(Fixtures.CHAVE_123)).isInstanceOf(UncheckedIOException.class);

        assertThat(tag(consulta(Fixtures.CHAVE_123).body(), "cStat")).isEqualTo("100");
        // A segunda tentativa, sem consultar antes, recebe duplicidade.
        assertThat(tag(autoriza(Fixtures.CHAVE_123).body(), "infProt")).contains("<cStat>204</cStat>");
    }

    @Test
    void servicePausedDevolve108NoStatusENaAutorizacao() {
        rule("""
                {"scenario": "SERVICE_PAUSED"}
                """);

        assertThat(tag(sefaz.soap("/ws", Fixtures.resource("requests/status.xml")).body(), "cStat")).isEqualTo("108");
        HttpResponse<String> autorizacao = autoriza(Fixtures.CHAVE_123);
        assertThat(tag(autorizacao.body(), "cStat")).isEqualTo("108");
        assertThat(autorizacao.body()).doesNotContain("protNFe");
        // A consulta não é afetada pelo cenário.
        assertThat(tag(consulta(Fixtures.CHAVE_123).body(), "cStat")).isEqualTo("217");
    }

    @Test
    void serviceUnavailableDevolve503ComFault() {
        rule("""
                {"scenario": "SERVICE_UNAVAILABLE", "times": 1}
                """);

        HttpResponse<String> response = sefaz.soap("/ws", Fixtures.resource("requests/status.xml"));

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("soap:Fault");
        assertThat(tag(sefaz.soap("/ws", Fixtures.resource("requests/status.xml")).body(), "cStat")).isEqualTo("107");
    }

    @Test
    void processingDevolve105PorAttemptsConsultas() {
        rule("""
                {"match": {"operation": "RET_AUTORIZACAO"}, "scenario": "PROCESSING", "params": {"attempts": 2}}
                """);
        String nRec = tag(sefaz.soap("/ws", Fixtures.autorizacao(Fixtures.CHAVE_123, false, "")).body(), "nRec");
        String consulta = Fixtures.resource("requests/ret-autorizacao.xml").replace("${nRec}", nRec);

        assertThat(tag(sefaz.soap("/ws", consulta).body(), "cStat")).isEqualTo("105");
        assertThat(tag(sefaz.soap("/ws", consulta).body(), "cStat")).isEqualTo("105");
        String terceira = sefaz.soap("/ws", consulta).body();
        assertThat(tag(terceira, "cStat")).isEqualTo("104");
        assertThat(terceira).contains("<cStat>100</cStat>");
    }

    @Test
    void reciboDesconhecidoDevolve106() {
        String consulta = Fixtures.resource("requests/ret-autorizacao.xml").replace("${nRec}", "351999999999999");

        assertThat(tag(sefaz.soap("/ws", consulta).body(), "cStat")).isEqualTo("106");
    }

    @Test
    void deniedGravaNotaDenegadaQueConsomeANumeracao() {
        rule("""
                {"match": {"numero": "123"}, "scenario": "DENIED", "params": {"cStat": "302"}, "times": 1}
                """);

        String infProt = tag(autoriza(Fixtures.CHAVE_123).body(), "infProt");
        assertThat(infProt).contains("<cStat>302</cStat>").contains("<nProt>");

        assertThat(tag(consulta(Fixtures.CHAVE_123).body(), "cStat")).isEqualTo("302");
        assertThat(tag(autoriza(Fixtures.CHAVE_123).body(), "infProt")).contains("<cStat>205</cStat>");
        assertThat(tag(autoriza(Fixtures.CHAVE_123_OUTRO_CNF).body(), "infProt")).contains("<cStat>539</cStat>");
    }

    @Test
    void rejectDevolveCStatEXMotivoDosParametros() {
        rule("""
                {"match": {"serie": "1"}, "scenario": "REJECT", "params": {"cStat": "999", "xMotivo": "Rejeição: <teste>"}, "times": 1}
                """);

        String infProt = tag(autoriza(Fixtures.CHAVE_123).body(), "infProt");

        assertThat(infProt).contains("<cStat>999</cStat>").contains("Rejeição: &lt;teste&gt;").doesNotContain("nProt");
        // Nota rejeitada não fica na base: reenviar autoriza.
        assertThat(tag(autoriza(Fixtures.CHAVE_123).body(), "infProt")).contains("<cStat>100</cStat>");
    }

    @Test
    void rejectNoNivelDoLote() {
        rule("""
                {"scenario": "REJECT", "params": {"cStat": "656", "nivel": "lote"}}
                """);

        String body = autoriza(Fixtures.CHAVE_123).body();

        assertThat(tag(body, "cStat")).isEqualTo("656");
        assertThat(body).doesNotContain("protNFe");
    }

    @Test
    void slowRespondeNormalmenteDepoisDoAtraso() {
        rule("""
                {"match": {"operation": "STATUS_SERVICO"}, "scenario": "SLOW", "params": {"delayMs": 400}}
                """);

        long inicio = System.nanoTime();
        String body = sefaz.soap("/ws", Fixtures.resource("requests/status.xml")).body();

        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isGreaterThanOrEqualTo(Duration.ofMillis(400));
        assertThat(tag(body, "cStat")).isEqualTo("107");
    }

    @Test
    void regraInvalidaDevolve400() {
        assertThat(sefaz.admin("POST", "/scenarios", "{\"scenario\": \"NAO_EXISTE\"}").statusCode()).isEqualTo(400);
        assertThat(sefaz.admin("POST", "/scenarios", "{\"scenario\": \"REJECT\"}").body()).contains("cStat");
        assertThat(sefaz.admin("POST", "/scenarios", "{\"scenario\": \"SLOW\", \"tempo\": 1}").statusCode()).isEqualTo(400);
        assertThat(sefaz.admin("POST", "/scenarios", "{\"match\": {\"chave\": \"123\"}, \"scenario\": \"SLOW\"}")
                .statusCode()).isEqualTo(400);
    }

    @Test
    void apiAdminListaAlteraERemoveRegras() {
        rule("{\"scenario\": \"SLOW\", \"params\": {\"delayMs\": 10}}");

        assertThat(json(sefaz.admin("GET", "/scenarios", null)).get(0).get("rule").get("id").asText()).isEqualTo("rule-1");
        assertThat(sefaz.admin("PUT", "/scenarios/rule-1", "{\"scenario\": \"SLOW\", \"times\": 1}").statusCode())
                .isEqualTo(200);
        assertThat(json(sefaz.admin("GET", "/scenarios", null)).get(0).get("remaining").asInt()).isEqualTo(1);
        assertThat(sefaz.admin("DELETE", "/scenarios/rule-1", null).statusCode()).isEqualTo(204);
        assertThat(sefaz.admin("DELETE", "/scenarios/rule-1", null).statusCode()).isEqualTo(404);
    }

    @Test
    void apiAdminMostraNotaEResetLimpaTudo() {
        autoriza(Fixtures.CHAVE_123);
        rule("{\"scenario\": \"SLOW\"}");

        JsonNode nota = json(sefaz.admin("GET", "/notas/" + Fixtures.CHAVE_123, null));
        assertThat(nota.get("cStat").asText()).isEqualTo("100");
        assertThat(nota.get("nProt").asText()).hasSize(15);
        assertThat(nota.get("scenario").asText()).isEqualTo("HAPPY_PATH");
        assertThat(nota.get("xml").asText()).contains("<infNFe");

        assertThat(sefaz.admin("DELETE", "/state", null).statusCode()).isEqualTo(204);

        assertThat(sefaz.admin("GET", "/notas/" + Fixtures.CHAVE_123, null).statusCode()).isEqualTo(404);
        assertThat(json(sefaz.admin("GET", "/scenarios", null))).isEmpty();
        assertThat(json(sefaz.admin("GET", "/requests", null))).isEmpty();
        // Numeração do protocolo recomeça.
        assertThat(tag(autoriza(Fixtures.CHAVE_123).body(), "nProt")).isEqualTo("135260000000001");
    }

    @Test
    void healthETruststore() throws Exception {
        HttpResponse<String> health = sefaz.admin("GET", "/health", null);
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(json(health).get("status").asText()).isEqualTo("UP");

        byte[] truststore = sefaz.server.tls().trustStorePkcs12();
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new ByteArrayInputStream(truststore), "changeit".toCharArray());
        assertThat(keyStore.getCertificate("sefaz-mock-server")).isEqualTo(sefaz.server.tls().certificate());
    }

    @Test
    void validacaoPorXsdDevolve215QuandoOConteudoNaoPassa() {
        try (TestServer comXsd = new TestServer(ServerConfig.ephemeral()
                .withXsdDirectory(Path.of("src/test/resources/xsd")))) {
            String consultaInvalida = Fixtures.resource("requests/consulta.xml").replace("${chave}", "123");
            String consultaValida = Fixtures.resource("requests/consulta.xml").replace("${chave}", Fixtures.CHAVE_123);

            assertThat(tag(comXsd.soap("/ws", consultaInvalida).body(), "cStat")).isEqualTo("215");
            assertThat(tag(comXsd.soap("/ws", consultaValida).body(), "cStat")).isEqualTo("217");
            // Operações sem XSD no diretório não são validadas.
            assertThat(tag(comXsd.soap("/ws", Fixtures.resource("requests/status.xml")).body(), "cStat")).isEqualTo("107");
        }
    }

    @Test
    void cenarioPadraoDoContainer() {
        try (TestServer pausado = new TestServer(ServerConfig.ephemeral()
                .withDefaultScenario(new ScenarioContext(Scenario.SERVICE_PAUSED, Map.of(), null)))) {
            assertThat(tag(pausado.soap("/ws", Fixtures.resource("requests/status.xml")).body(), "cStat")).isEqualTo("108");
            JsonNode history = json(pausado.admin("GET", "/requests", null));
            assertThat(history.get(0).get("rule").asText()).isEqualTo("default");
        }
    }

    @Test
    void configuracaoPorVariaveisDeAmbiente() {
        ServerConfig config = ServerConfig.fromEnvironment(Map.of(
                "SEFAZ_DEFAULT_SCENARIO", "slow",
                "SEFAZ_DEFAULT_SCENARIO_PARAMS", "{\"delayMs\": 5}",
                "SEFAZ_CLIENT_AUTH", "need",
                "SEFAZ_TLS_SANS", "172.17.0.1, docker-host")::get);

        assertThat(config.defaultScenario().scenario()).isEqualTo(Scenario.SLOW);
        assertThat(config.defaultScenario().delayMs()).isEqualTo(5);
        assertThat(config.clientAuth()).isEqualTo(io.github.vbaggio.sefaz.mock.tls.ClientAuth.NEED);
        assertThat(config.tlsSans()).containsExactly("172.17.0.1", "docker-host");
        assertThat(config.httpsPort()).isEqualTo(8443);
        assertThatThrownBy(() -> ServerConfig.fromEnvironment(Map.of("SEFAZ_DEFAULT_SCENARIO", "REJECT")::get))
                .hasMessageContaining("cStat");
    }

    private void rule(String json) {
        HttpResponse<String> response = sefaz.admin("POST", "/scenarios", json);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    }

    private HttpResponse<String> autoriza(String chave) {
        return sefaz.soap("/ws/NFeAutorizacao4", Fixtures.autorizacao(chave, true, ""));
    }

    private HttpResponse<String> consulta(String chave) {
        return sefaz.soap("/ws/NFeConsultaProtocolo4", Fixtures.resource("requests/consulta.xml").replace("${chave}", chave));
    }

    private static JsonNode json(HttpResponse<String> response) {
        try {
            return Json.MAPPER.readTree(response.body());
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
