package io.github.vbaggio.sefaz.mock;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Testes de contrato (ADR-002): a resposta de cada operação é comparada byte a byte com uma
 * fixture em {@code src/test/resources/fixtures/responses}, com o relógio fixo.
 *
 * <p>Para regravar as fixtures depois de uma mudança intencional:
 * {@code ./gradlew :sefaz-mock-server:test -PupdateFixtures} e revise o diff.
 */
class ContractTest {

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
    void statusDoServico() {
        assertMatches("status.xml", sefaz.soap("/ws/NFeStatusServico4.asmx", Fixtures.resource("requests/status.xml")));
    }

    @Test
    void statusDoServicoEmSoap11RepeteTpAmb() {
        HttpResponse<String> response = sefaz.soap("/ws/NFeStatusServico4", "text/xml; charset=utf-8",
                Fixtures.resource("requests/status-soap11.xml"), java.time.Duration.ofSeconds(5));
        assertThat(response.headers().firstValue("Content-Type")).hasValue("text/xml; charset=utf-8");
        assertMatches("status-soap11.xml", response);
    }

    @Test
    void autorizacaoSincrona() {
        assertMatches("autorizacao-sincrona.xml", autoriza(Fixtures.CHAVE_123, true, "ZGlnZXN0LWZpY3RpY2lv"));
    }

    @Test
    void autorizacaoAssincronaERetorno() {
        assertMatches("autorizacao-assincrona.xml", autoriza(Fixtures.CHAVE_123, false, ""));
        String retorno = Fixtures.resource("requests/ret-autorizacao.xml").replace("${nRec}", "351000000000001");
        assertMatches("ret-autorizacao.xml", sefaz.soap("/ws/NFeRetAutorizacao4", retorno));
    }

    @Test
    void consultaDeNotaAutorizadaEDesconhecida() {
        autoriza(Fixtures.CHAVE_123, true, "ZGlnZXN0LWZpY3RpY2lv");
        assertMatches("consulta-autorizada.xml", consulta(Fixtures.CHAVE_123));
        assertMatches("consulta-desconhecida.xml", consulta(Fixtures.CHAVE_124));
    }

    @Test
    void duplicidades() {
        autoriza(Fixtures.CHAVE_123, true, "");
        assertMatches("autorizacao-duplicidade-204.xml", autoriza(Fixtures.CHAVE_123, true, ""));
        assertMatches("autorizacao-duplicidade-539.xml", autoriza(Fixtures.CHAVE_123_OUTRO_CNF, true, ""));
    }

    @Test
    void servicoNaoSuportadoDevolveFault() {
        HttpResponse<String> response = sefaz.soap("/ws/NFeInutilizacao4", Fixtures.resource("requests/servico-desconhecido.xml"));
        assertThat(response.statusCode()).isEqualTo(500);
        assertMatches("fault-servico-desconhecido.xml", response);
    }

    private HttpResponse<String> autoriza(String chave, boolean sincrono, String digest) {
        return sefaz.soap("/ws/NFeAutorizacao4", Fixtures.autorizacao(chave, sincrono, digest));
    }

    private HttpResponse<String> consulta(String chave) {
        return sefaz.soap("/ws/NFeConsultaProtocolo4", Fixtures.resource("requests/consulta.xml").replace("${chave}", chave));
    }

    private static void assertMatches(String fixture, HttpResponse<String> response) {
        String actual = normalize(response.body());
        Path file = Path.of("src/test/resources/fixtures/responses", fixture);
        try {
            if (System.getProperty("sefaz.updateFixtures") != null) {
                Files.writeString(file, actual + "\n", StandardCharsets.UTF_8);
            }
            assertThat(actual).isEqualTo(Fixtures.resource("responses/" + fixture).strip());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** A versão da aplicação muda a cada release; o resto da resposta é contrato. */
    private static String normalize(String xml) {
        return xml.replaceAll("<verAplic>[^<]*</verAplic>", "<verAplic>SEFAZ-MOCK</verAplic>");
    }
}
