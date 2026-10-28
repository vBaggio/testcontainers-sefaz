package io.github.vbaggio.sefaz.mock;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.vbaggio.sefaz.mock.admin.Json;
import io.github.vbaggio.sefaz.mock.scenario.Scenario;
import io.github.vbaggio.sefaz.mock.scenario.ScenarioContext;
import io.github.vbaggio.sefaz.mock.tls.ClientAuth;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Configuração do servidor, lida de variáveis de ambiente.
 *
 * <ul>
 *   <li>{@code SEFAZ_DEFAULT_SCENARIO} e {@code SEFAZ_DEFAULT_SCENARIO_PARAMS} (JSON)
 *   <li>{@code SEFAZ_TLS_SANS}: nomes e IPs extras do certificado, separados por vírgula
 *   <li>{@code SEFAZ_CLIENT_AUTH}: {@code none}, {@code want} (padrão) ou {@code need}
 *   <li>{@code SEFAZ_XSD_DIR}: diretório com XSDs para validação opcional
 *   <li>{@code SEFAZ_HTTPS_PORT}, {@code SEFAZ_HTTP_PORT}, {@code SEFAZ_ADMIN_PORT}: 0 = porta livre
 * </ul>
 */
public record ServerConfig(
        int httpsPort,
        int httpPort,
        int adminPort,
        ScenarioContext defaultScenario,
        List<String> tlsSans,
        ClientAuth clientAuth,
        Path xsdDirectory,
        int historyLimit) {

    public static ServerConfig defaults() {
        return new ServerConfig(8443, 8080, 8081, ScenarioContext.happyPath(), List.of(), ClientAuth.WANT, null, 10_000);
    }

    /** Portas livres, para testes que sobem o servidor no mesmo processo. */
    public static ServerConfig ephemeral() {
        return defaults().withPorts(0, 0, 0);
    }

    public ServerConfig withPorts(int https, int http, int admin) {
        return new ServerConfig(https, http, admin, defaultScenario, tlsSans, clientAuth, xsdDirectory, historyLimit);
    }

    public ServerConfig withDefaultScenario(ScenarioContext scenario) {
        return new ServerConfig(httpsPort, httpPort, adminPort, scenario, tlsSans, clientAuth, xsdDirectory,
                historyLimit);
    }

    public ServerConfig withClientAuth(ClientAuth mode) {
        return new ServerConfig(httpsPort, httpPort, adminPort, defaultScenario, tlsSans, mode, xsdDirectory,
                historyLimit);
    }

    public ServerConfig withXsdDirectory(Path dir) {
        return new ServerConfig(httpsPort, httpPort, adminPort, defaultScenario, tlsSans, clientAuth, dir,
                historyLimit);
    }

    public static ServerConfig fromEnvironment(Function<String, String> env) {
        ServerConfig d = defaults();
        return new ServerConfig(
                intVar(env, "SEFAZ_HTTPS_PORT", d.httpsPort()),
                intVar(env, "SEFAZ_HTTP_PORT", d.httpPort()),
                intVar(env, "SEFAZ_ADMIN_PORT", d.adminPort()),
                defaultScenario(env.apply("SEFAZ_DEFAULT_SCENARIO"), env.apply("SEFAZ_DEFAULT_SCENARIO_PARAMS")),
                sans(env.apply("SEFAZ_TLS_SANS")),
                ClientAuth.parse(env.apply("SEFAZ_CLIENT_AUTH")),
                blank(env.apply("SEFAZ_XSD_DIR")) ? null : Path.of(env.apply("SEFAZ_XSD_DIR")),
                intVar(env, "SEFAZ_HISTORY_LIMIT", d.historyLimit()));
    }

    private static ScenarioContext defaultScenario(String name, String paramsJson) {
        if (blank(name)) {
            return ScenarioContext.happyPath();
        }
        Map<String, Object> params = Map.of();
        if (!blank(paramsJson)) {
            try {
                params = Json.MAPPER.readValue(paramsJson, new TypeReference<Map<String, Object>>() {
                });
            } catch (IOException e) {
                throw new IllegalArgumentException("SEFAZ_DEFAULT_SCENARIO_PARAMS não é um JSON válido: " + paramsJson, e);
            }
        }
        Scenario scenario = Scenario.parse(name);
        // Valida os parâmetros como uma regra faria.
        new io.github.vbaggio.sefaz.mock.scenario.ScenarioRule(null, null, scenario, params, null);
        return new ScenarioContext(scenario, params, null);
    }

    private static List<String> sans(String value) {
        if (blank(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static int intVar(Function<String, String> env, String name, int fallback) {
        String value = env.apply(name);
        if (blank(value)) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " deve ser numérico: " + value, e);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
