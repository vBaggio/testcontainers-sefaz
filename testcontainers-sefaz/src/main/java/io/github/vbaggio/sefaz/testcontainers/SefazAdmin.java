package io.github.vbaggio.sefaz.testcontainers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cliente da API admin da SEFAZ fake: configura cenários, inspeciona requisições e notas e reseta
 * o estado.
 *
 * <pre>{@code
 * sefaz.admin()
 *      .when(Match.serie("1").numero("123"))
 *      .then(Scenario.timeoutAfterAuthorize(Duration.ofSeconds(10)))
 *      .times(1);
 * }</pre>
 */
public final class SefazAdmin {

    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final String baseUrl;
    private final HttpClient http;

    /** @param adminUrl URL da porta admin, por exemplo {@code http://localhost:32768} */
    public SefazAdmin(String adminUrl) {
        this.baseUrl = (adminUrl.endsWith("/") ? adminUrl.substring(0, adminUrl.length() - 1) : adminUrl) + "/__admin";
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /** Começa uma regra: {@code when(match).then(scenario)} registra, {@code .times(n)} limita. */
    public RuleBuilder when(Match match) {
        return new RuleBuilder(match);
    }

    /** Registra uma regra de uma vez. {@code times} nulo = sem limite. */
    public ScenarioRule register(Match match, Scenario scenario, Integer times) {
        JsonNode created = send("POST", "/scenarios", ruleJson(match, scenario, times), 201);
        return toRule(created);
    }

    /** Regras ativas, na ordem de registro. */
    public List<ScenarioRule> rules() {
        JsonNode list = send("GET", "/scenarios", null, 200);
        List<ScenarioRule> rules = new ArrayList<>();
        list.forEach(view -> rules.add(toRule(view.get("rule"))));
        return rules;
    }

    public void removeRule(String id) {
        send("DELETE", "/scenarios/" + encode(id), null, 204);
    }

    /** Todas as requisições recebidas desde o último reset, em ordem de chegada. */
    public List<RecordedRequest> requests() {
        return read(send("GET", "/requests", null, 200), new TypeReference<>() {
        });
    }

    public List<RecordedRequest> requests(SefazService service) {
        return read(send("GET", "/requests?operation=" + service.name(), null, 200), new TypeReference<>() {
        });
    }

    /** Estado de uma nota recebida, inclusive rejeitada; vazio se a chave nunca chegou. */
    public Optional<NotaState> nota(String chave) {
        HttpResponse<String> response = raw("GET", "/notas/" + encode(chave), null);
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        check(response, 200);
        return Optional.of(read(parse(response.body()), new TypeReference<>() {
        }));
    }

    public List<NotaState> notas() {
        return read(send("GET", "/notas", null, 200), new TypeReference<>() {
        });
    }

    /** Limpa notas, recibos, regras e histórico. */
    public void reset() {
        send("DELETE", "/state", null, 204);
    }

    byte[] trustStorePkcs12() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/tls/truststore"))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Truststore indisponível: HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** Segunda metade do {@code when(...).then(...)}. */
    public final class RuleBuilder {
        private final Match match;

        private RuleBuilder(Match match) {
            this.match = match;
        }

        /** Registra a regra sem limite de aplicações. */
        public RegisteredRule then(Scenario scenario) {
            return new RegisteredRule(register(match, scenario, null), match, scenario);
        }
    }

    /** Regra já registrada; {@link #times(int)} limita quantas vezes ela se aplica. */
    public final class RegisteredRule {
        private final ScenarioRule rule;
        private final Match match;
        private final Scenario scenario;

        private RegisteredRule(ScenarioRule rule, Match match, Scenario scenario) {
            this.rule = rule;
            this.match = match;
            this.scenario = scenario;
        }

        /** A regra expira depois de {@code n} aplicações (ADR-006). */
        public ScenarioRule times(int n) {
            if (n < 1) {
                throw new IllegalArgumentException("times deve ser maior que zero");
            }
            return toRule(send("PUT", "/scenarios/" + encode(rule.id()), ruleJson(match, scenario, n), 200));
        }

        public String id() {
            return rule.id();
        }

        public ScenarioRule rule() {
            return rule;
        }
    }

    private static String ruleJson(Match match, Scenario scenario, Integer times) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (match.operation() != null) {
            m.put("operation", match.operation().name());
        }
        putIfNotNull(m, "chave", match.chave());
        putIfNotNull(m, "cnpj", match.cnpj());
        putIfNotNull(m, "serie", match.serie());
        putIfNotNull(m, "numero", match.numero());
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("match", m);
        rule.put("scenario", scenario.name());
        rule.put("params", scenario.params());
        if (times != null) {
            rule.put("times", times);
        }
        try {
            return JSON.writeValueAsString(rule);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ScenarioRule toRule(JsonNode node) {
        JsonNode m = node.path("match");
        Match.Criteria match = Match.any();
        String operation = text(m, "operation");
        if (operation != null) {
            for (SefazService service : SefazService.values()) {
                if (service.name().equals(operation)) {
                    match = match.operation(service);
                }
            }
        }
        if (text(m, "chave") != null) {
            match = match.chave(text(m, "chave"));
        }
        if (text(m, "cnpj") != null) {
            match = match.cnpj(text(m, "cnpj"));
        }
        if (text(m, "serie") != null) {
            match = match.serie(text(m, "serie"));
        }
        if (text(m, "numero") != null) {
            match = match.numero(text(m, "numero"));
        }
        Map<String, Object> params = node.has("params")
                ? JSON.convertValue(node.get("params"), new TypeReference<Map<String, Object>>() {
                })
                : Map.of();
        Integer times = node.hasNonNull("times") ? node.get("times").asInt() : null;
        return new ScenarioRule(text(node, "id"), match, Scenario.of(text(node, "scenario"), params), times);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private JsonNode send(String method, String path, String body, int expectedStatus) {
        HttpResponse<String> response = raw(method, path, body);
        check(response, expectedStatus);
        return response.body() == null || response.body().isEmpty() ? JSON.nullNode() : parse(response.body());
    }

    private HttpResponse<String> raw(String method, String path, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(30));
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        try {
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException("API admin da SEFAZ fake inacessível em " + baseUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void check(HttpResponse<String> response, int expectedStatus) {
        if (response.statusCode() != expectedStatus) {
            throw new IllegalStateException("API admin da SEFAZ fake respondeu HTTP " + response.statusCode() + " em "
                    + response.request().method() + " " + response.uri() + ": " + response.body());
        }
    }

    private static JsonNode parse(String body) {
        try {
            return JSON.readTree(body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static <T> T read(JsonNode node, TypeReference<T> type) {
        return JSON.convertValue(node, type);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
