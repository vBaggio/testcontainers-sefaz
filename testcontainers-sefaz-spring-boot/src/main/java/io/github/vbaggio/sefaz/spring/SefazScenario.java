package io.github.vbaggio.sefaz.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Regra de cenário aplicada antes do método de teste (ou de todos os métodos, se na classe). Depois
 * de cada teste o estado da SEFAZ fake é resetado.
 *
 * <pre>{@code
 * @Test
 * @SefazScenario(numero = "123", scenario = "SERVICE_PAUSED")
 * void entraEmContingenciaQuandoSefazParalisa() { ... }
 * }</pre>
 *
 * <p>Campos vazios não restringem a regra, como em {@code Match}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(SefazScenarios.class)
public @interface SefazScenario {

    /** Nome do cenário, por exemplo {@code TIMEOUT_AFTER_AUTHORIZE}. */
    String scenario();

    /** Nome de um {@code SefazService}, por exemplo {@code AUTORIZACAO}. */
    String operation() default "";

    String chave() default "";

    String cnpj() default "";

    String serie() default "";

    String numero() default "";

    /** Parâmetros no formato {@code nome=valor}, por exemplo {@code "delayMs=2000"}. */
    String[] params() default {};

    /** Aplicações antes de expirar; 0 = sem limite. */
    int times() default 0;
}
