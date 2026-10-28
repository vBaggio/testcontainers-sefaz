package io.github.vbaggio.sefaz.testcontainers;

/**
 * Regra registrada na SEFAZ fake.
 *
 * @param times aplicações antes de expirar; {@code null} = sem limite
 */
public record ScenarioRule(String id, Match match, Scenario scenario, Integer times) {
}
