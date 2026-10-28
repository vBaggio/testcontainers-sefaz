package io.github.vbaggio.sefaz.spring;

import io.github.vbaggio.sefaz.testcontainers.SefazContainer;
import java.util.List;
import java.util.Objects;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.TestContextAnnotationUtils;

/**
 * Coloca a URL da SEFAZ fake na propriedade indicada por {@link SefazTest#urlProperty()}. A URL é
 * lida só quando a propriedade é usada, e o container é iniciado nesse momento se ainda não estiver.
 */
final class SefazPropertyMapping implements ContextCustomizer {

    static final String PROPERTY_SOURCE_NAME = "sefazTest";

    private final SefazContainer container;
    private final String urlProperty;
    private final boolean http;

    SefazPropertyMapping(SefazContainer container, String urlProperty, boolean http) {
        this.container = container;
        this.urlProperty = urlProperty;
        this.http = http;
    }

    @Override
    public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
        context.getEnvironment().getPropertySources().addFirst(new UrlPropertySource(this));
    }

    String url() {
        SefazContainer running = SefazContainers.started(container);
        return http ? running.getHttpBaseUrl() : running.getBaseUrl();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SefazPropertyMapping other
                && container == other.container
                && urlProperty.equals(other.urlProperty)
                && http == other.http;
    }

    @Override
    public int hashCode() {
        return Objects.hash(System.identityHashCode(container), urlProperty, http);
    }

    private static final class UrlPropertySource extends EnumerablePropertySource<SefazPropertyMapping> {

        UrlPropertySource(SefazPropertyMapping mapping) {
            super(PROPERTY_SOURCE_NAME, mapping);
        }

        @Override
        public String[] getPropertyNames() {
            return new String[] {getSource().urlProperty};
        }

        @Override
        public Object getProperty(String name) {
            return getSource().urlProperty.equals(name) ? getSource().url() : null;
        }
    }

    /** Registrada em {@code META-INF/spring.factories}; só age em classes com {@link SefazTest}. */
    static final class Factory implements ContextCustomizerFactory {

        @Override
        public ContextCustomizer createContextCustomizer(Class<?> testClass,
                List<ContextConfigurationAttributes> configAttributes) {
            SefazTest annotation = TestContextAnnotationUtils.findMergedAnnotation(testClass, SefazTest.class);
            if (annotation == null) {
                return null;
            }
            SefazContainer container = SefazContainers.staticField(testClass).orElseThrow(() -> new IllegalStateException(
                    "@SefazTest em " + testClass.getName() + " exige um campo estático do tipo SefazContainer"));
            return new SefazPropertyMapping(container, annotation.urlProperty(), annotation.http());
        }
    }
}
