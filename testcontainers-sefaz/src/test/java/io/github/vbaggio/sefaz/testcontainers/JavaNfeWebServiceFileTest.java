package io.github.vbaggio.sefaz.testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JavaNfeWebServiceFileTest {

    @Test
    void geraSecoesDeHomologacaoEProducaoDeNfeENfce() {
        String ini = JavaNfeWebServiceFile.render("http://localhost:8080/", Estado.PR);

        assertThat(ini).contains("""
                [NFCe_PR_H]
                NfeStatusServico_4.00=http://localhost:8080/ws/NFeStatusServico4
                NfeAutorizacao_4.00=http://localhost:8080/ws/NFeAutorizacao4
                NFeRetAutorizacao_4.00=http://localhost:8080/ws/NFeRetAutorizacao4
                NfeConsultaProtocolo_4.00=http://localhost:8080/ws/NFeConsultaProtocolo4
                URL-QRCode=http://localhost:8080/nfce/qrcode
                URL-ConsultaNFCe=http://localhost:8080/nfce/consulta
                """);
        assertThat(ini).contains("[NFe_PR_H]", "[NFe_PR_P]", "[NFCe_PR_P]", "[NFe_SVRS_H]", "[NFe_SVC-AN_P]");
    }

    @Test
    void exigeUf() {
        assertThatThrownBy(() -> JavaNfeWebServiceFile.render("http://x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void matchEncadeiaCampos() {
        Match match = Match.serie("1").numero("123").operation(SefazService.AUTORIZACAO);

        assertThat(match.serie()).isEqualTo("1");
        assertThat(match.numero()).isEqualTo("123");
        assertThat(match.operation()).isEqualTo(SefazService.AUTORIZACAO);
        assertThat(Match.numero(123)).isEqualTo(Match.numero("123"));
    }
}
