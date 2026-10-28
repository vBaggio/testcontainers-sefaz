package io.github.vbaggio.sefaz.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Liga a URL da SEFAZ fake a uma propriedade da aplicação. Não existe propriedade padrão de URL da
 * SEFAZ, então o teste diz qual propriedade deve recebê-la.
 *
 * <pre>{@code
 * @SpringBootTest
 * @SefazTest(urlProperty = "emissor.sefaz.url")
 * class EmissaoServiceIT {
 *     @Container
 *     static SefazContainer sefaz = new SefazContainer();
 * }
 * }</pre>
 *
 * <p>O container é o campo estático do tipo {@code SefazContainer} da classe de teste.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface SefazTest {

    /** Propriedade que recebe a URL base, por exemplo {@code emissor.sefaz.url}. */
    String urlProperty();

    /** {@code true} entrega a URL HTTP (porta 8080) em vez da HTTPS. */
    boolean http() default false;
}
