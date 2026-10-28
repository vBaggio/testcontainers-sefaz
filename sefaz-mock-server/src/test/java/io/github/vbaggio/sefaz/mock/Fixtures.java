package io.github.vbaggio.sefaz.mock;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Dados fictícios dos testes. O CNPJ 11.222.333/0001-81 é o exemplo de documentação usual. */
final class Fixtures {

    static final String CHAVE_123 = "35261011222333000181550010000001231123456783";
    static final String CHAVE_124 = "35261011222333000181550010000001241123456780";
    /** Mesma numeração da {@link #CHAVE_123} (série 1, número 123), outro cNF. */
    static final String CHAVE_123_OUTRO_CNF = "35261011222333000181550010000001231876543217";

    private Fixtures() {
    }

    static String resource(String path) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + path)) {
            if (in == null) {
                throw new IllegalArgumentException("Fixture não encontrada: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Envelope de autorização com uma nota; {@code digest} vazio = nota sem assinatura. */
    static String autorizacao(String chave, boolean sincrono, String digest) {
        return resource("requests/autorizacao.xml")
                .replace("${chave}", chave)
                .replace("${indSinc}", sincrono ? "1" : "0")
                .replace("${signature}", digest == null ? "" : resource("requests/signature.xml").replace("${digest}", digest));
    }
}
