package io.github.vbaggio.sefaz.mock.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.github.vbaggio.sefaz.mock.scenario.ScenarioRegistry;
import io.github.vbaggio.sefaz.mock.scenario.ScenarioRule;
import io.github.vbaggio.sefaz.mock.store.NotaStore;
import io.github.vbaggio.sefaz.mock.tls.TlsMaterial;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * API admin na porta 8081: health, histórico, notas, regras de cenário, reset e truststore.
 *
 * <p>Fica numa porta HTTP separada para que testes configurem cenários sem depender de TLS.
 */
public final class AdminHttpHandler implements HttpHandler {

    public static final String PREFIX = "/__admin";
    private static final Logger LOG = Logger.getLogger(AdminHttpHandler.class.getName());

    private final ScenarioRegistry scenarios;
    private final NotaStore store;
    private final RequestHistory history;
    private final TlsMaterial tls;
    private final String version;

    public AdminHttpHandler(ScenarioRegistry scenarios, NotaStore store, RequestHistory history, TlsMaterial tls,
            String version) {
        this.scenarios = scenarios;
        this.store = store;
        this.history = history;
        this.tls = tls;
        this.version = version;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Sem try-with-resources: a troca precisa continuar aberta para o catch responder o erro.
        try {
            route(exchange);
        } catch (BadRequest e) {
            json(exchange, 400, Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Erro na API admin", e);
            json(exchange, 500, Map.of("error", String.valueOf(e.getMessage())));
        } finally {
            exchange.close();
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        String path = exchange.getRequestURI().getPath();
        String route = path.startsWith(PREFIX) ? path.substring(PREFIX.length()) : path;
        if (route.endsWith("/") && route.length() > 1) {
            route = route.substring(0, route.length() - 1);
        }

        switch (method + " " + route) {
            case "GET /health" -> json(exchange, 200, Map.of("status", "UP", "version", version));
            case "GET /requests" -> json(exchange, 200, history.list(queryParam(exchange, "operation")));
            case "GET /notas" -> json(exchange, 200, store.all().stream().map(n -> NotaView.of(n, false)).toList());
            case "GET /scenarios" -> json(exchange, 200, scenarios.list());
            case "POST /scenarios" -> json(exchange, 201, scenarios.add(readRule(exchange)));
            case "DELETE /state" -> {
                store.clear();
                scenarios.clear();
                history.clear();
                exchange.sendResponseHeaders(204, -1);
            }
            case "GET /tls/truststore" -> {
                exchange.getResponseHeaders().set("X-Truststore-Password", TlsMaterial.TRUSTSTORE_PASSWORD);
                bytes(exchange, 200, "application/x-pkcs12", tls.trustStorePkcs12());
            }
            case "GET /tls/certificate" ->
                    bytes(exchange, 200, "application/x-pem-file", tls.certificatePem().getBytes(StandardCharsets.US_ASCII));
            default -> routeWithId(exchange, method, route);
        }
    }

    private void routeWithId(HttpExchange exchange, String method, String route) throws IOException {
        if (route.startsWith("/notas/") && method.equals("GET")) {
            String chave = decode(route.substring("/notas/".length())).toUpperCase(Locale.ROOT);
            var nota = store.find(chave);
            if (nota.isPresent()) {
                json(exchange, 200, NotaView.of(nota.get(), true));
            } else {
                json(exchange, 404, Map.of("error", "Nota não encontrada: " + chave));
            }
            return;
        }
        if (route.startsWith("/scenarios/")) {
            String id = decode(route.substring("/scenarios/".length()));
            switch (method) {
                case "PUT" -> {
                    var replaced = scenarios.replace(id, readRule(exchange));
                    if (replaced.isPresent()) {
                        json(exchange, 200, replaced.get());
                    } else {
                        json(exchange, 404, Map.of("error", "Regra não encontrada: " + id));
                    }
                    return;
                }
                case "DELETE" -> {
                    if (scenarios.remove(id)) {
                        exchange.sendResponseHeaders(204, -1);
                    } else {
                        json(exchange, 404, Map.of("error", "Regra não encontrada: " + id));
                    }
                    return;
                }
                default -> {
                }
            }
        }
        json(exchange, 404, Map.of("error", "Rota não encontrada: " + method + " " + PREFIX + route));
    }

    private static ScenarioRule readRule(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return Json.MAPPER.readValue(in, ScenarioRule.class);
        } catch (ValueInstantiationException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new BadRequest(cause.getMessage());
        } catch (JsonProcessingException e) {
            throw new BadRequest("Regra inválida: " + e.getOriginalMessage());
        }
    }

    private static String queryParam(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (decode(kv[0]).equals(name)) {
                return kv.length > 1 ? decode(kv[1]) : "";
            }
        }
        return null;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void json(HttpExchange exchange, int status, Object body) throws IOException {
        bytes(exchange, status, "application/json", Json.MAPPER.writeValueAsBytes(body));
    }

    private static void bytes(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static final class BadRequest extends RuntimeException {
        BadRequest(String message) {
            super(message);
        }
    }
}
