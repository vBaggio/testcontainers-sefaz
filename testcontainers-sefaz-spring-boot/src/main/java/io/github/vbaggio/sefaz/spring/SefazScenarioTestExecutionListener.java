package io.github.vbaggio.sefaz.spring;

import io.github.vbaggio.sefaz.testcontainers.Match;
import io.github.vbaggio.sefaz.testcontainers.Scenario;
import io.github.vbaggio.sefaz.testcontainers.SefazContainer;
import io.github.vbaggio.sefaz.testcontainers.SefazService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * Aplica as anotações {@link SefazScenario} antes de cada teste e reseta a SEFAZ fake depois.
 * Registrado em {@code META-INF/spring.factories}; não faz nada em testes sem {@code SefazContainer}.
 */
public class SefazScenarioTestExecutionListener extends AbstractTestExecutionListener {

    /** Depois da injeção de dependências, antes de transações. */
    @Override
    public int getOrder() {
        return 3_500;
    }

    @Override
    public void beforeTestMethod(TestContext testContext) {
        List<SefazScenario> scenarios = new ArrayList<>(AnnotatedElementUtils.findMergedRepeatableAnnotations(
                testContext.getTestClass(), SefazScenario.class, SefazScenarios.class));
        scenarios.addAll(AnnotatedElementUtils.findMergedRepeatableAnnotations(
                testContext.getTestMethod(), SefazScenario.class, SefazScenarios.class));
        if (scenarios.isEmpty()) {
            return;
        }
        SefazContainer container = container(testContext).orElseThrow(() -> new IllegalStateException(
                "@SefazScenario em " + testContext.getTestMethod() + " exige um SefazContainer no teste"));
        SefazContainers.started(container);
        for (SefazScenario scenario : scenarios) {
            container.admin().register(match(scenario), Scenario.of(scenario.scenario(), params(scenario)),
                    scenario.times() > 0 ? scenario.times() : null);
        }
    }

    @Override
    public void afterTestMethod(TestContext testContext) {
        container(testContext).filter(SefazContainer::isRunning).ifPresent(c -> c.admin().reset());
    }

    private static Optional<SefazContainer> container(TestContext testContext) {
        Optional<SefazContainer> field = SefazContainers.staticField(testContext.getTestClass());
        if (field.isPresent() || !testContext.hasApplicationContext()) {
            return field;
        }
        return Optional.ofNullable(
                testContext.getApplicationContext().getBeanProvider(SefazContainer.class).getIfAvailable());
    }

    static Match match(SefazScenario s) {
        Match.Criteria match = Match.any();
        if (!s.operation().isEmpty()) {
            match = match.operation(SefazService.valueOf(s.operation()));
        }
        if (!s.chave().isEmpty()) {
            match = match.chave(s.chave());
        }
        if (!s.cnpj().isEmpty()) {
            match = match.cnpj(s.cnpj());
        }
        if (!s.serie().isEmpty()) {
            match = match.serie(s.serie());
        }
        if (!s.numero().isEmpty()) {
            match = match.numero(s.numero());
        }
        return match;
    }

    static Map<String, Object> params(SefazScenario s) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (String pair : s.params()) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("Parâmetro de @SefazScenario deve ser nome=valor: " + pair);
            }
            String value = pair.substring(eq + 1).trim();
            params.put(pair.substring(0, eq).trim(), value.matches("-?\\d+") ? (Object) Long.parseLong(value) : value);
        }
        return params;
    }
}
