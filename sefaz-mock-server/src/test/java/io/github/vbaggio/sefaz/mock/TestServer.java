package io.github.vbaggio.sefaz.mock;

import io.github.vbaggio.sefaz.mock.nfe.SefazClock;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Servidor no mesmo processo, em portas livres e com relógio fixo, mais um cliente HTTP simples. */
final class TestServer implements AutoCloseable {

    static final Instant AGORA = Instant.parse("2026-10-28T13:00:00Z");
    static final String SOAP12 = "application/soap+xml; charset=utf-8";

    final SefazMockServer server;
    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    TestServer(ServerConfig config) {
        this.server = SefazMockServer.start(config, new SefazClock(Clock.fixed(AGORA, SefazClock.BRASILIA)));
    }

    TestServer() {
        this(ServerConfig.ephemeral());
    }

    HttpResponse<String> soap(String path, String body) {
        return soap(path, SOAP12, body, Duration.ofSeconds(10));
    }

    HttpResponse<String> soap(String path, String contentType, String body, Duration timeout) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + server.httpPort() + path))
                .timeout(timeout)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return send(request);
    }

    HttpResponse<String> admin(String method, String path, String json) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                URI.create("http://localhost:" + server.adminPort() + "/__admin" + path));
        if (json == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json));
        }
        return send(builder.build());
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** Valor do primeiro elemento com esse nome local; suficiente para asserções. */
    static String tag(String xml, String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("<" + name + "(\\s[^>]*)?>(.*?)</" + name + ">", java.util.regex.Pattern.DOTALL)
                .matcher(xml);
        return m.find() ? m.group(2) : null;
    }

    @Override
    public void close() {
        server.close();
    }
}
