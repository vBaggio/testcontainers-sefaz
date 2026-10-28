package io.github.vbaggio.sefaz.mock.scenario;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Regra de cenário, no mesmo formato aceito pela API admin, pelo {@code SefazContainer} e pela
 * anotação do módulo Spring.
 *
 * @param times quantas vezes a regra se aplica antes de expirar; {@code null} = sem limite
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScenarioRule(String id, RuleMatch match, Scenario scenario, Map<String, Object> params, Integer times) {

    @JsonCreator
    public ScenarioRule(
            @JsonProperty("id") String id,
            @JsonProperty("match") RuleMatch match,
            @JsonProperty("scenario") Scenario scenario,
            @JsonProperty("params") Map<String, Object> params,
            @JsonProperty("times") Integer times) {
        if (scenario == null) {
            throw new IllegalArgumentException("Campo 'scenario' é obrigatório");
        }
        if (times != null && times < 1) {
            throw new IllegalArgumentException("Campo 'times' deve ser maior que zero");
        }
        this.id = id;
        this.match = RuleMatch.orAny(match);
        this.scenario = scenario;
        this.params = params == null ? Map.of() : Map.copyOf(params);
        this.times = times;
        ScenarioContext.validate(scenario, this.params);
    }

    ScenarioRule withId(String newId) {
        return new ScenarioRule(newId, match, scenario, params, times);
    }
}
