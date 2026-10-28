package io.github.vbaggio.sefaz.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vbaggio.sefaz.mock.xml.SecureXml;
import io.github.vbaggio.sefaz.mock.xml.Template;
import io.github.vbaggio.sefaz.mock.xml.XmlException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SecureXmlTest {

    @Test
    void recusaDtdComEntidadeExterna() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <consSitNFe><chNFe>&xxe;</chNFe></consSitNFe>
                """;

        assertThatThrownBy(() -> SecureXml.parse(stream(xxe)))
                .isInstanceOf(XmlException.class)
                .hasMessageContaining("DOCTYPE");
    }

    @Test
    void recusaEntidadeInternaExpandida() {
        String bomb = """
                <?xml version="1.0"?>
                <!DOCTYPE lolz [<!ENTITY lol "lol"><!ENTITY lol2 "&lol;&lol;&lol;">]>
                <a>&lol2;</a>
                """;

        assertThatThrownBy(() -> SecureXml.parse(stream(bomb))).isInstanceOf(XmlException.class);
    }

    @Test
    void escapaValoresVindosDaRequisicao() {
        assertThat(SecureXml.escape("<a href=\"x\">&'</a>"))
                .isEqualTo("&lt;a href=&quot;x&quot;&gt;&amp;&apos;&lt;/a&gt;");
        assertThat(SecureXml.escape("ok\u0000\u0007")).isEqualTo("ok");
    }

    @Test
    void templateEscapaPlaceholderSimplesENaoOTriplo() {
        String xml = Template.load("retConsSitNFe").render(Map.of(
                "tpAmb", "2",
                "verAplic", "v",
                "cStat", "217",
                "xMotivo", "x",
                "cUF", "35",
                "dhRecbto", "d",
                "chNFe", "</chNFe><cStat>100</cStat>",
                "protocolo", "<protNFe/>"));

        assertThat(xml)
                .contains("<chNFe>&lt;/chNFe&gt;&lt;cStat&gt;100&lt;/cStat&gt;</chNFe>")
                .contains("<protNFe/>")
                .doesNotContain("\n");
    }

    @Test
    void templateExigeTodosOsValores() {
        assertThatThrownBy(() -> Template.load("infRec").render(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nRec");
    }

    private static ByteArrayInputStream stream(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }
}
