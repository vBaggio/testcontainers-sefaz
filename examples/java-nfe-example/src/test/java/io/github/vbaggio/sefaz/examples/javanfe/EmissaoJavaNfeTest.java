package io.github.vbaggio.sefaz.examples.javanfe;

import static io.github.vbaggio.sefaz.testcontainers.SefazService.AUTORIZACAO;
import static io.github.vbaggio.sefaz.testcontainers.SefazService.CONSULTA_PROTOCOLO;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.swconsultoria.certificado.Certificado;
import br.com.swconsultoria.nfe.Nfe;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import br.com.swconsultoria.nfe.dom.enuns.AmbienteEnum;
import br.com.swconsultoria.nfe.dom.enuns.DocumentoEnum;
import br.com.swconsultoria.nfe.dom.enuns.EstadosEnum;
import br.com.swconsultoria.nfe.schemas.TEnviNFe;
import io.github.vbaggio.sefaz.examples.javanfe.ResultadoEmissao.Status;
import io.github.vbaggio.sefaz.testcontainers.Estado;
import io.github.vbaggio.sefaz.testcontainers.Match;
import io.github.vbaggio.sefaz.testcontainers.NotaState;
import io.github.vbaggio.sefaz.testcontainers.Scenario;
import io.github.vbaggio.sefaz.testcontainers.SefazContainer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Emissão real pela Java_NFe contra a SEFAZ fake: o arquivo de web services gerado pelo container
 * substitui o da biblioteca, e o certificado A1 é fictício.
 */
@Testcontainers
class EmissaoJavaNfeTest {

    /** Timeout de cliente menor que o atraso dos cenários de timeout. */
    private static final int TIMEOUT_CLIENTE_MS = 1_000;
    private static final Duration ATRASO = Duration.ofSeconds(2);

    @Container
    static SefazContainer sefaz = new SefazContainer(System.getProperty("sefaz.image",
            SefazContainer.DEFAULT_IMAGE_NAME.withTag(SefazContainer.DEFAULT_TAG).asCanonicalNameString()));

    @TempDir
    static Path dir;

    static Certificado certificado;
    static Path arquivoHttp;
    static Path arquivoHttps;
    static byte[] cacert;

    private Emissor emissor;

    @BeforeAll
    static void prepara() throws Exception {
        certificado = CertificadoA1Ficticio.certificado(NotaFicticia.CNPJ, NotaFicticia.RAZAO_SOCIAL);
        // A Java_NFe aceita http:// no arquivo customizado (ADR-005); o TLS fica para um teste próprio.
        arquivoHttp = sefaz.writeJavaNfeWebServiceFile(dir.resolve("http"), sefaz.getHttpBaseUrl(), Estado.SP);
        arquivoHttps = sefaz.writeJavaNfeWebServiceFile(dir.resolve("https"), Estado.SP);
        // A Java_Certificado abre o cacert com a senha fixa "changeit".
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        sefaz.getTrustStore().store(out, "changeit".toCharArray());
        cacert = out.toByteArray();
        Files.createDirectories(dir.resolve("schemas"));
    }

    @BeforeEach
    void limpa() {
        sefaz.admin().reset();
        emissor = new Emissor(configuracoes(arquivoHttp, false));
    }

    @Test
    void statusDoServico() throws Exception {
        assertThat(Nfe.statusServico(configuracoes(arquivoHttp, false).get(), DocumentoEnum.NFE).getCStat())
                .isEqualTo("107");
    }

    @Test
    void statusDoServicoPorHttpsComCacertDoContainer() throws Exception {
        Supplier<ConfiguracoesNfe> https = configuracoes(arquivoHttps, true);

        // Duas chamadas: a segunda prova que o cacert é entregue de novo a cada operação.
        assertThat(Nfe.statusServico(https.get(), DocumentoEnum.NFE).getCStat()).isEqualTo("107");
        assertThat(Nfe.statusServico(https.get(), DocumentoEnum.NFE).getCStat()).isEqualTo("107");
        assertThat(sefaz.admin().requests()).allSatisfy(r -> assertThat(r.clientCertificate()).contains(NotaFicticia.CNPJ));
    }

    @Test
    void autorizacaoSincrona() throws Exception {
        TEnviNFe lote = NotaFicticia.numero(100);

        ResultadoEmissao resultado = emissor.emitir(lote);

        assertThat(resultado.status()).isEqualTo(Status.AUTORIZADA);
        assertThat(resultado.nProt()).hasSize(15);
        assertThat(resultado.recuperadaPorConsulta()).isFalse();
        NotaState nota = sefaz.admin().nota(NotaFicticia.chave(lote)).orElseThrow();
        assertThat(nota.autorizada()).isTrue();
        // A Java_NFe assinou a nota; o digVal do protocolo é o DigestValue da assinatura.
        assertThat(nota.digVal()).isNotEmpty();
        assertThat(nota.xml()).contains("<Signature");
    }

    @Test
    void naoReenviaNotaJaAutorizadaAposTimeout() throws Exception {
        sefaz.admin()
                .when(Match.numero("123"))
                .then(Scenario.timeoutAfterAuthorize(ATRASO))
                .times(1);

        var resultado = emissor.emitir(NotaFicticia.numero(123));

        assertThat(resultado.status()).isEqualTo(Status.AUTORIZADA);
        assertThat(resultado.recuperadaPorConsulta()).isTrue();
        assertThat(sefaz.admin().requests(AUTORIZACAO)).hasSize(1);
        assertThat(sefaz.admin().requests(CONSULTA_PROTOCOLO)).hasSize(1);
    }

    @Test
    void reenviaQuandoOTimeoutFoiAntesDeAutorizar() throws Exception {
        sefaz.admin()
                .when(Match.numero("124"))
                .then(Scenario.timeoutBeforeAuthorize(ATRASO))
                .times(1);

        var resultado = emissor.emitir(NotaFicticia.numero(124));

        assertThat(resultado.status()).isEqualTo(Status.AUTORIZADA);
        assertThat(resultado.tentativas()).isEqualTo(2);
        assertThat(sefaz.admin().requests(AUTORIZACAO)).hasSize(2);
        assertThat(sefaz.admin().requests(CONSULTA_PROTOCOLO)).hasSize(1);
    }

    @Test
    void recuperaAutorizacaoQuandoAConexaoCai() throws Exception {
        sefaz.admin()
                .when(Match.numero("125"))
                .then(Scenario.connectionResetAfterAuthorize())
                .times(1);

        var resultado = emissor.emitir(NotaFicticia.numero(125));

        assertThat(resultado.status()).isEqualTo(Status.AUTORIZADA);
        assertThat(sefaz.admin().nota(resultado.chave()).orElseThrow().autorizada()).isTrue();
    }

    @Test
    void entraEmContingenciaQuandoSefazParalisa() throws Exception {
        sefaz.admin().when(Match.any()).then(Scenario.servicePaused());

        var resultado = emissor.emitir(NotaFicticia.numero(126));

        assertThat(resultado.status()).isEqualTo(Status.CONTINGENCIA);
        assertThat(resultado.cStat()).isEqualTo("108");
        assertThat(sefaz.admin().requests(AUTORIZACAO)).isEmpty();
    }

    @Test
    void entraEmContingenciaQuandoSefazNaoResponde() throws Exception {
        sefaz.admin().when(Match.any()).then(Scenario.serviceUnavailable());

        var resultado = emissor.emitir(NotaFicticia.numero(127));

        assertThat(resultado.status()).isEqualTo(Status.CONTINGENCIA);
        assertThat(sefaz.admin().requests(AUTORIZACAO)).isEmpty();
    }

    @Test
    void denegacao() throws Exception {
        sefaz.admin().when(Match.numero("128")).then(Scenario.denied("302")).times(1);

        var resultado = emissor.emitir(NotaFicticia.numero(128));

        assertThat(resultado.status()).isEqualTo(Status.DENEGADA);
        assertThat(resultado.cStat()).isEqualTo("302");
    }

    private Supplier<ConfiguracoesNfe> configuracoes(Path arquivoWebService, boolean comCacert) {
        return () -> {
            try {
                ConfiguracoesNfe config = ConfiguracoesNfe.criarConfiguracoes(EstadosEnum.SP, AmbienteEnum.HOMOLOGACAO,
                        certificado, dir.resolve("schemas").toString());
                config.setArquivoWebService(arquivoWebService.toString());
                config.setTimeout(TIMEOUT_CLIENTE_MS);
                if (comCacert) {
                    config.setCacert(new ByteArrayInputStream(cacert));
                }
                return config;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }
}
