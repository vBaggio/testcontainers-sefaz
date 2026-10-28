package io.github.vbaggio.sefaz.mock.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Template de texto para as respostas (ADR-002).
 *
 * <p>{@code {{nome}}} recebe o valor escapado; {@code {{{nome}}}} recebe XML já montado, sem escape,
 * e só é usado para fragmentos produzidos pelo próprio servidor.
 */
public final class Template {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\{)?([a-zA-Z0-9_]+)}}(})?");
    private static final Map<String, Template> CACHE = new ConcurrentHashMap<>();

    private final String name;
    private final String text;

    private Template(String name, String text) {
        this.name = name;
        this.text = text;
    }

    public static Template load(String name) {
        return CACHE.computeIfAbsent(name, n -> {
            String path = "/templates/" + n + ".xml";
            try (InputStream in = Template.class.getResourceAsStream(path)) {
                if (in == null) {
                    throw new IllegalStateException("Template não encontrado: " + path);
                }
                // Os arquivos têm quebra de linha para leitura; a resposta sai numa linha só.
                String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return new Template(n, raw.replaceAll("\\R\\s*", "").strip());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public String render(Map<String, ?> values) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder(text.length() + 256);
        while (matcher.find()) {
            boolean raw = matcher.group(1) != null && matcher.group(3) != null;
            String key = matcher.group(2);
            if (!values.containsKey(key)) {
                throw new IllegalArgumentException("Template " + name + " sem valor para " + key);
            }
            Object value = values.get(key);
            String rendered = value == null ? "" : value.toString();
            matcher.appendReplacement(out, Matcher.quoteReplacement(raw ? rendered : SecureXml.escape(rendered)));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
