package io.github.vbaggio.sefaz.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vbaggio.sefaz.mock.nfe.ChaveAcesso;
import io.github.vbaggio.sefaz.mock.nfe.Numeracao;
import org.junit.jupiter.api.Test;

class ChaveAcessoTest {

    @Test
    void decompoeChavePorPosicao() {
        ChaveAcesso chave = ChaveAcesso.parse(Fixtures.CHAVE_123);

        assertThat(chave.cUF()).isEqualTo("35");
        assertThat(chave.aamm()).isEqualTo("2610");
        assertThat(chave.cnpj()).isEqualTo("11222333000181");
        assertThat(chave.modelo()).isEqualTo("55");
        assertThat(chave.serie()).isEqualTo("001");
        assertThat(chave.numero()).isEqualTo("000000123");
        assertThat(chave.tpEmis()).isEqualTo("1");
        assertThat(chave.cNF()).isEqualTo("12345678");
        assertThat(chave.dv()).isEqualTo("3");
    }

    @Test
    void aceitaPrefixoNFeDoAtributoId() {
        assertThat(ChaveAcesso.parse("NFe" + Fixtures.CHAVE_123).valor()).isEqualTo(Fixtures.CHAVE_123);
    }

    @Test
    void aceitaCnpjAlfanumerico() {
        ChaveAcesso chave = ChaveAcesso.parse("3526101ABC2DEF000135550010000001231123456780");

        assertThat(chave.cnpj()).isEqualTo("1ABC2DEF000135");
        assertThat(chave.numero()).isEqualTo("000000123");
    }

    @Test
    void numeracaoIgnoraZerosAEsquerda() {
        Numeracao numeracao = ChaveAcesso.parse(Fixtures.CHAVE_123).numeracao();

        assertThat(numeracao).isEqualTo(new Numeracao("11222333000181", "55", "1", "123"));
    }

    @Test
    void recusaChaveComTamanhoErrado() {
        assertThatThrownBy(() -> ChaveAcesso.parse("3526"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("44 posições");
        assertThat(ChaveAcesso.isValida(Fixtures.CHAVE_123 + "<")).isFalse();
    }
}
