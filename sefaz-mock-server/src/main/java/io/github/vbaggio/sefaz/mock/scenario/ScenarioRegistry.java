package io.github.vbaggio.sefaz.mock.scenario;

import io.github.vbaggio.sefaz.mock.nfe.ChaveAcesso;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Regras de cenário registradas e a resolução do cenário de cada requisição.
 *
 * <p>A primeira regra que casar vence, nesta ordem: regras que casam com a nota, regras que casam
 * só com a operação, cenário padrão do container. Dentro de cada grupo vale a ordem de registro.
 */
public final class ScenarioRegistry {

    private final List<ActiveRule> rules = new ArrayList<>();
    private final AtomicLong ids = new AtomicLong();
    private final ScenarioContext defaultScenario;

    public ScenarioRegistry(ScenarioContext defaultScenario) {
        this.defaultScenario = defaultScenario;
    }

    public synchronized ScenarioRule add(ScenarioRule rule) {
        ScenarioRule stored = rule.withId("rule-" + ids.incrementAndGet());
        rules.add(new ActiveRule(stored));
        return stored;
    }

    /** Substitui uma regra mantendo sua posição; a contagem de aplicações recomeça. */
    public synchronized Optional<ScenarioRule> replace(String id, ScenarioRule rule) {
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).rule.id().equals(id)) {
                ScenarioRule stored = rule.withId(id);
                rules.set(i, new ActiveRule(stored));
                return Optional.of(stored);
            }
        }
        return Optional.empty();
    }

    public synchronized boolean remove(String id) {
        return rules.removeIf(r -> r.rule.id().equals(id));
    }

    public synchronized List<RuleView> list() {
        return rules.stream().map(ActiveRule::view).toList();
    }

    public synchronized void clear() {
        rules.clear();
    }

    public ScenarioContext defaultScenario() {
        return defaultScenario;
    }

    /** Escolhe o cenário e consome uma aplicação da regra escolhida. */
    public synchronized ScenarioContext resolve(String operation, List<ChaveAcesso> chaves) {
        ActiveRule chosen = find(operation, chaves, true);
        if (chosen == null) {
            chosen = find(operation, chaves, false);
        }
        if (chosen != null) {
            chosen.applied++;
            if (chosen.expired()) {
                rules.remove(chosen);
            }
            return new ScenarioContext(chosen.rule.scenario(), chosen.rule.params(), chosen.rule.id());
        }
        if (defaultScenario.scenario().appliesTo(operation)) {
            return defaultScenario;
        }
        return ScenarioContext.happyPath();
    }

    private ActiveRule find(String operation, List<ChaveAcesso> chaves, boolean notaRules) {
        for (ActiveRule active : rules) {
            ScenarioRule rule = active.rule;
            RuleMatch match = rule.match();
            if (match.hasNotaFields() != notaRules
                    || !match.matchesOperation(operation)
                    || !rule.scenario().appliesTo(operation)) {
                continue;
            }
            if (!notaRules || chaves.stream().anyMatch(match::matchesNota)) {
                return active;
            }
        }
        return null;
    }

    /** Regra como exposta pela API admin. */
    public record RuleView(ScenarioRule rule, int applied, Integer remaining) {
    }

    private static final class ActiveRule {
        private final ScenarioRule rule;
        private int applied;

        private ActiveRule(ScenarioRule rule) {
            this.rule = rule;
        }

        private boolean expired() {
            return rule.times() != null && applied >= rule.times();
        }

        private RuleView view() {
            return new RuleView(rule, applied, rule.times() == null ? null : rule.times() - applied);
        }
    }
}
