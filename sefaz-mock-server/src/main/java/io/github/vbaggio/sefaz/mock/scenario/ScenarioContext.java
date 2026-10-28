package io.github.vbaggio.sefaz.mock.scenario;

import java.util.Map;

/**
 * Cenário escolhido para uma requisição e a origem da escolha.
 *
 * @param ruleId id da regra aplicada; {@code null} quando veio do cenário padrão do container
 */
public record ScenarioContext(Scenario scenario, Map<String, Object> params, String ruleId) {

    public static final long DEFAULT_DELAY_MS = 60_000;
    public static final long DEFAULT_SLOW_DELAY_MS = 3_000;
    public static final int DEFAULT_ATTEMPTS = 3;
    public static final String DEFAULT_DENIED_CSTAT = "301";

    public ScenarioContext {
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    public static ScenarioContext happyPath() {
        return new ScenarioContext(Scenario.HAPPY_PATH, Map.of(), null);
    }

    public boolean is(Scenario other) {
        return scenario == other;
    }

    /** Origem para o histórico: o id da regra ou "default". */
    public String source() {
        return ruleId == null ? "default" : ruleId;
    }

    public long delayMs() {
        long fallback = scenario == Scenario.SLOW ? DEFAULT_SLOW_DELAY_MS : DEFAULT_DELAY_MS;
        return longParam(params, "delayMs", fallback);
    }

    public int attempts() {
        return (int) longParam(params, "attempts", DEFAULT_ATTEMPTS);
    }

    public String cStat(String fallback) {
        Object value = params.get("cStat");
        return value == null ? fallback : value.toString();
    }

    public String xMotivo(String fallback) {
        Object value = params.get("xMotivo");
        return value == null ? fallback : value.toString();
    }

    /** {@code REJECT}: "nota" (padrão) rejeita dentro do protNFe; "lote" rejeita o lote inteiro. */
    public boolean rejeitaLote() {
        return "lote".equalsIgnoreCase(String.valueOf(params.getOrDefault("nivel", "nota")));
    }

    static void validate(Scenario scenario, Map<String, Object> params) {
        if (params.containsKey("delayMs") && longParam(params, "delayMs", 0) < 0) {
            throw new IllegalArgumentException("delayMs não pode ser negativo");
        }
        if (params.containsKey("attempts") && longParam(params, "attempts", 0) < 0) {
            throw new IllegalArgumentException("attempts não pode ser negativo");
        }
        Object cStat = params.get("cStat");
        if (cStat != null && !cStat.toString().matches("\\d{3}")) {
            throw new IllegalArgumentException("cStat deve ter 3 dígitos: " + cStat);
        }
        if (scenario == Scenario.REJECT && cStat == null) {
            throw new IllegalArgumentException("Cenário REJECT exige o parâmetro cStat");
        }
    }

    private static long longParam(Map<String, Object> params, String name, long fallback) {
        Object value = params.get(name);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Parâmetro " + name + " deve ser numérico: " + value);
        }
    }
}
