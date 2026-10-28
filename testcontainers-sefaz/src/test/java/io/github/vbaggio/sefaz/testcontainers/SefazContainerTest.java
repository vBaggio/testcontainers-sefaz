package io.github.vbaggio.sefaz.testcontainers;

import static io.github.vbaggio.sefaz.testcontainers.Soap.cStatNota;
import static io.github.vbaggio.sefaz.testcontainers.Soap.tag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Integração contra a imagem construída neste build: cada cenário do catálogo e a API admin. */
@Testcontainers
class SefazContainerTest {

    static final String IMAGE = System.getProperty("sefaz.image", SefazContainer.DEFAULT_IMAGE_NAME
            .withTag(SefazContainer.DEFAULT_TAG).asCanonicalNameString());

    @Container
    static SefazContainer sefaz = new SefazContainer(IMAGE);

    private HttpClient https;

    @BeforeEach
    void setUp() {
        sefaz.admin().reset();
        https = Soap.client(sefaz.getTrustStore());
    }

    @Test
    void statusPorHttpsEHttp() throws Exception {
        assertThat(tag(post(SefazService.STATUS_SERVICO, Soap.status()).body(), "cStat")).isEqualTo("107");

        HttpResponse<String> http = Soap.post(HttpClient.newHttpClient(), sefaz.getHttpUrl(SefazService.STATUS_SERVICO),
                Soap.status(), Duration.ofSeconds(5));
        assertThat(tag(http.body(), "cStat")).isEqualTo("107");
        assertThat(sefaz.getUrl(SefazService.AUTORIZACAO)).startsWith("https://").endsWith("/ws/NFeAutorizacao4");
    }

    @Test
    void happyPathAutorizaEConsulta() throws Exception {
        String chave = Soap.chave(1);

        String autorizacao = post(SefazService.AUTORIZACAO, Soap.autorizacao(chave, true)).body();

        assertThat(cStatNota(autorizacao)).isEqualTo("100");
        assertThat(tag(autorizacao, "nProt")).hasSize(15);
        assertThat(tag(post(SefazService.CONSULTA_PROTOCOLO, Soap.consulta(chave)).body(), "cStat")).isEqualTo("100");
        NotaState nota = sefaz.admin().nota(chave).orElseThrow();
        assertThat(nota.autorizada()).isTrue();
        assertThat(nota.scenario()).isEqualTo("HAPPY_PATH");
        assertThat(sefaz.admin().nota(Soap.chave(2))).isEmpty();
    }

    @Test
    void timeoutAfterAuthorize() throws Exception {
        String chave = Soap.chave(123);
        sefaz.admin()
                .when(Match.serie("1").numero("123"))
                .then(Scenario.timeoutAfterAuthorize(Duration.ofSeconds(2)))
                .times(1);

        assertThatThrownBy(() -> Soap.post(https, sefaz.getUrl(SefazService.AUTORIZACAO), Soap.autorizacao(chave, true),
                Duration.ofMillis(500))).isInstanceOf(HttpTimeoutException.class);

        assertThat(tag(post(SefazService.CONSULTA_PROTOCOLO, Soap.consulta(chave)).body(), "cStat")).isEqualTo("100");
        assertThat(sefaz.admin().requests(SefazService.AUTORIZACAO))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.scenario()).isEqualTo("TIMEOUT_AFTER_AUTHORIZE");
                    assertThat(r.delivery()).isEqualTo("DELAYED");
                    assertThat(r.chaves()).containsExactly(chave);
                });
        assertThat(sefaz.admin().requests(SefazService.CONSULTA_PROTOCOLO)).hasSize(1);
        assertThat(sefaz.admin().rules()).isEmpty();
    }

    @Test
    void timeoutBeforeAuthorize() throws Exception {
        String chave = Soap.chave(124);
        sefaz.admin().when(Match.chave(chave)).then(Scenario.timeoutBeforeAuthorize(Duration.ofMillis(300)));

        HttpResponse<String> response = post(SefazService.AUTORIZACAO, Soap.autorizacao(chave, true));

        assertThat(response.statusCode()).isEqualTo(504);
        assertThat(tag(post(SefazService.CONSULTA_PROTOCOLO, Soap.consulta(chave)).body(), "cStat")).isEqualTo("217");
    }

    @Test
    void connectionResetAfterAuthorize() throws Exception {
        String chave = Soap.chave(125);
        sefaz.admin().when(Match.numero(125)).then(Scenario.connectionResetAfterAuthorize()).times(1);

        assertThatThrownBy(() -> post(SefazService.AUTORIZACAO, Soap.autorizacao(chave, true)))
                .isInstanceOf(IOException.class);

        assertThat(tag(post(SefazService.CONSULTA_PROTOCOLO, Soap.consulta(chave)).body(), "cStat")).isEqualTo("100");
        assertThat(sefaz.admin().requests(SefazService.AUTORIZACAO).get(0).delivery()).isEqualTo("CONNECTION_CLOSED");
    }

    @Test
    void servicePaused() throws Exception {
        sefaz.admin().when(Match.any()).then(Scenario.servicePaused());

        assertThat(tag(post(SefazService.STATUS_SERVICO, Soap.status()).body(), "cStat")).isEqualTo("108");
        assertThat(tag(post(SefazService.AUTORIZACAO, Soap.autorizacao(Soap.chave(3), true)).body(), "cStat"))
                .isEqualTo("108");
    }

    @Test
    void serviceUnavailable() throws Exception {
        sefaz.admin().when(Match.operation(SefazService.STATUS_SERVICO)).then(Scenario.serviceUnavailable()).times(1);

        HttpResponse<String> response = post(SefazService.STATUS_SERVICO, Soap.status());

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("Fault");
        assertThat(post(SefazService.STATUS_SERVICO, Soap.status()).statusCode()).isEqualTo(200);
    }

    @Test
    void processing() throws Exception {
        sefaz.admin().when(Match.operation(SefazService.RET_AUTORIZACAO)).then(Scenario.processing(2));
        String recebido = post(SefazService.AUTORIZACAO, Soap.autorizacao(Soap.chave(4), false)).body();
        assertThat(tag(recebido, "cStat")).isEqualTo("103");
        String nRec = tag(recebido, "nRec");

        assertThat(tag(post(SefazService.RET_AUTORIZACAO, Soap.retAutorizacao(nRec)).body(), "cStat")).isEqualTo("105");
        assertThat(tag(post(SefazService.RET_AUTORIZACAO, Soap.retAutorizacao(nRec)).body(), "cStat")).isEqualTo("105");
        String processado = post(SefazService.RET_AUTORIZACAO, Soap.retAutorizacao(nRec)).body();
        assertThat(tag(processado, "cStat")).isEqualTo("104");
        assertThat(cStatNota(processado)).isEqualTo("100");
    }

    @Test
    void denied() throws Exception {
        String chave = Soap.chave(5);
        sefaz.admin().when(Match.chave(chave)).then(Scenario.denied()).times(1);

        assertThat(cStatNota(post(SefazService.AUTORIZACAO, Soap.autorizacao(chave, true)).body())).isEqualTo("301");
        assertThat(tag(post(SefazService.CONSULTA_PROTOCOLO, Soap.consulta(chave)).body(), "cStat")).isEqualTo("301");
        assertThat(sefaz.admin().nota(chave).orElseThrow().naBase()).isTrue();
    }

    @Test
    void reject() throws Exception {
        String chave = Soap.chave(6);
        sefaz.admin().when(Match.cnpj("11.222.333/0001-81")).then(Scenario.reject("778", "Rejeição: teste")).times(1);

        String body = post(SefazService.AUTORIZACAO, Soap.autorizacao(chave, true)).body();

        assertThat(cStatNota(body)).isEqualTo("778");
        assertThat(tag(body, "xMotivo")).isEqualTo("Lote processado");
        assertThat(cStatNota(post(SefazService.AUTORIZACAO, Soap.autorizacao(chave, true)).body())).isEqualTo("100");
    }

    @Test
    void slow() throws Exception {
        sefaz.admin().when(Match.operation(SefazService.STATUS_SERVICO)).then(Scenario.slow(Duration.ofMillis(500)));

        long inicio = System.nanoTime();
        HttpResponse<String> response = post(SefazService.STATUS_SERVICO, Soap.status());

        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isGreaterThanOrEqualTo(Duration.ofMillis(500));
        assertThat(tag(response.body(), "cStat")).isEqualTo("107");
    }

    @Test
    void duplicidades() throws Exception {
        String chave = Soap.chave(7);
        post(SefazService.AUTORIZACAO, Soap.autorizacao(chave, true));

        assertThat(cStatNota(post(SefazService.AUTORIZACAO, Soap.autorizacao(chave, true)).body())).isEqualTo("204");
        String outraChaveMesmoNumero = chave.substring(0, 35) + "87654321" + "0";
        assertThat(cStatNota(post(SefazService.AUTORIZACAO, Soap.autorizacao(outraChaveMesmoNumero, true)).body()))
                .isEqualTo("539");
    }

    @Test
    void regrasPelaApiAdmin() {
        var regra = sefaz.admin().when(Match.numero("9")).then(Scenario.slow(Duration.ofMillis(10)));

        assertThat(sefaz.admin().rules()).singleElement().satisfies(r -> {
            assertThat(r.id()).isEqualTo(regra.id());
            assertThat(r.match()).isEqualTo(Match.numero("9"));
            assertThat(r.scenario()).isEqualTo(Scenario.slow(Duration.ofMillis(10)));
            assertThat(r.times()).isNull();
        });
        assertThat(regra.times(2).times()).isEqualTo(2);
        sefaz.admin().removeRule(regra.id());
        assertThat(sefaz.admin().rules()).isEmpty();
        assertThatThrownBy(() -> sefaz.admin().register(Match.any(), Scenario.of("NAO_EXISTE", java.util.Map.of()), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("400");
    }

    @Test
    void writeTrustStoreGravaPkcs12ComASenhaPedida(@TempDir Path dir) throws Exception {
        Path file = sefaz.writeTrustStore(dir, "segredo".toCharArray());

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(file)) {
            keyStore.load(in, "segredo".toCharArray());
        }
        assertThat(keyStore.size()).isEqualTo(1);
    }

    @Test
    void writeJavaNfeWebServiceFile(@TempDir Path dir) throws Exception {
        Path file = sefaz.writeJavaNfeWebServiceFile(dir, Estado.SP, Estado.MG);

        String ini = Files.readString(file);
        assertThat(file.getFileName().toString()).isEqualTo("WebServicesNfe.ini");
        assertThat(ini)
                .contains("[NFe_SP_H]", "[NFe_SP_P]", "[NFCe_SP_H]", "[NFe_MG_H]", "[NFe_SVC-AN_H]", "[NFe_SVRS_P]")
                .contains("NfeAutorizacao_4.00=" + sefaz.getUrl(SefazService.AUTORIZACAO))
                .contains("NfeConsultaProtocolo_4.00=" + sefaz.getUrl(SefazService.CONSULTA_PROTOCOLO))
                .doesNotContain("[NFe_RJ_H]");
    }

    private HttpResponse<String> post(SefazService service, String envelope) throws IOException, InterruptedException {
        return Soap.post(https, sefaz.getUrl(service), envelope, Duration.ofSeconds(10));
    }
}
