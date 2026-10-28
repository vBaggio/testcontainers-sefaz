package io.github.vbaggio.sefaz.mock;

import static io.github.vbaggio.sefaz.mock.handler.Operations.AUTORIZACAO;
import static io.github.vbaggio.sefaz.mock.handler.Operations.CONSULTA_PROTOCOLO;
import static io.github.vbaggio.sefaz.mock.handler.Operations.STATUS_SERVICO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.vbaggio.sefaz.mock.nfe.ChaveAcesso;
import io.github.vbaggio.sefaz.mock.scenario.RuleMatch;
import io.github.vbaggio.sefaz.mock.scenario.Scenario;
import io.github.vbaggio.sefaz.mock.scenario.ScenarioContext;
import io.github.vbaggio.sefaz.mock.scenario.ScenarioRegistry;
import io.github.vbaggio.sefaz.mock.scenario.ScenarioRule;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScenarioRegistryTest {

    private static final List<ChaveAcesso> NOTA_123 = List.of(ChaveAcesso.parse(Fixtures.CHAVE_123));
    private static final List<ChaveAcesso> NOTA_124 = List.of(ChaveAcesso.parse(Fixtures.CHAVE_124));

    private final ScenarioRegistry registry = new ScenarioRegistry(ScenarioContext.happyPath());

    @Test
    void regraDaNotaVenceRegraDaOperacaoMesmoRegistradaDepois() {
        registry.add(rule(new RuleMatch(AUTORIZACAO, null, null, null, null), Scenario.SLOW, null));
        registry.add(rule(new RuleMatch(null, null, null, "1", "123"), Scenario.DENIED, null));

        assertThat(registry.resolve(AUTORIZACAO, NOTA_123).scenario()).isEqualTo(Scenario.DENIED);
        assertThat(registry.resolve(AUTORIZACAO, NOTA_124).scenario()).isEqualTo(Scenario.SLOW);
    }

    @Test
    void numeroDaRegraCasaComNumeroComZerosNaChave() {
        registry.add(rule(new RuleMatch(null, null, null, null, "000123"), Scenario.DENIED, null));

        assertThat(registry.resolve(AUTORIZACAO, NOTA_123).scenario()).isEqualTo(Scenario.DENIED);
    }

    @Test
    void regraExpiraDepoisDeTimesAplicacoes() {
        ScenarioRule stored = registry.add(rule(new RuleMatch(null, Fixtures.CHAVE_123, null, null, null),
                Scenario.SERVICE_UNAVAILABLE, 2));

        assertThat(registry.resolve(AUTORIZACAO, NOTA_123).ruleId()).isEqualTo(stored.id());
        assertThat(registry.resolve(AUTORIZACAO, NOTA_123).ruleId()).isEqualTo(stored.id());
        assertThat(registry.resolve(AUTORIZACAO, NOTA_123).scenario()).isEqualTo(Scenario.HAPPY_PATH);
        assertThat(registry.list()).isEmpty();
    }

    @Test
    void falhaNaPrimeiraTentativaEAutorizaNaSegunda() {
        registry.add(rule(new RuleMatch(null, null, null, null, "123"), Scenario.TIMEOUT_AFTER_AUTHORIZE, 1));

        assertThat(registry.resolve(AUTORIZACAO, NOTA_123).scenario()).isEqualTo(Scenario.TIMEOUT_AFTER_AUTHORIZE);
        assertThat(registry.resolve(AUTORIZACAO, NOTA_123).scenario()).isEqualTo(Scenario.HAPPY_PATH);
    }

    @Test
    void regraQueNaoSeAplicaAOperacaoNaoConsomeTimes() {
        registry.add(rule(new RuleMatch(null, null, null, null, "123"), Scenario.TIMEOUT_AFTER_AUTHORIZE, 1));

        // A consulta da mesma nota não é afetada e não gasta a aplicação da regra.
        assertThat(registry.resolve(CONSULTA_PROTOCOLO, NOTA_123).scenario()).isEqualTo(Scenario.HAPPY_PATH);
        assertThat(registry.resolve(AUTORIZACAO, NOTA_123).scenario()).isEqualTo(Scenario.TIMEOUT_AFTER_AUTHORIZE);
    }

    @Test
    void ordemDeRegistroDecideEntreRegrasDoMesmoGrupo() {
        registry.add(rule(new RuleMatch(null, null, null, null, "123"), Scenario.DENIED, null));
        registry.add(rule(new RuleMatch(null, Fixtures.CHAVE_123, null, null, null), Scenario.SLOW, null));

        assertThat(registry.resolve(AUTORIZACAO, NOTA_123).scenario()).isEqualTo(Scenario.DENIED);
    }

    @Test
    void cenarioPadraoSoValeParaOperacoesAQueSeAplica() {
        ScenarioRegistry paused = new ScenarioRegistry(new ScenarioContext(Scenario.SERVICE_PAUSED, Map.of(), null));

        assertThat(paused.resolve(STATUS_SERVICO, List.of()).scenario()).isEqualTo(Scenario.SERVICE_PAUSED);
        assertThat(paused.resolve(CONSULTA_PROTOCOLO, NOTA_123).scenario()).isEqualTo(Scenario.HAPPY_PATH);
    }

    @Test
    void regraSemCamposCasaComQualquerOperacao() {
        registry.add(rule(null, Scenario.SERVICE_UNAVAILABLE, null));

        assertThat(registry.resolve(STATUS_SERVICO, List.of()).scenario()).isEqualTo(Scenario.SERVICE_UNAVAILABLE);
        assertThat(registry.resolve(CONSULTA_PROTOCOLO, NOTA_123).scenario()).isEqualTo(Scenario.SERVICE_UNAVAILABLE);
    }

    @Test
    void rejectExigeCStat() {
        assertThatThrownBy(() -> rule(null, Scenario.REJECT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cStat");
        assertThatThrownBy(() -> new ScenarioRule(null, null, Scenario.REJECT, Map.of("cStat", "12"), null))
                .hasMessageContaining("3 dígitos");
        assertThatThrownBy(() -> rule(null, Scenario.SLOW, 0)).hasMessageContaining("times");
    }

    @Test
    void parametrosPadraoDosCenarios() {
        assertThat(new ScenarioContext(Scenario.TIMEOUT_AFTER_AUTHORIZE, Map.of(), null).delayMs()).isEqualTo(60_000);
        assertThat(new ScenarioContext(Scenario.PROCESSING, Map.of(), null).attempts()).isEqualTo(3);
        assertThat(new ScenarioContext(Scenario.SLOW, Map.of("delayMs", "250"), null).delayMs()).isEqualTo(250);
    }

    private static ScenarioRule rule(RuleMatch match, Scenario scenario, Integer times) {
        return new ScenarioRule(null, match, scenario, Map.of(), times);
    }
}
