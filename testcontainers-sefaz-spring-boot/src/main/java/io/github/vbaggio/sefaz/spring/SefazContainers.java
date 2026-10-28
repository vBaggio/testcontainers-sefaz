package io.github.vbaggio.sefaz.spring;

import io.github.vbaggio.sefaz.testcontainers.SefazContainer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Optional;

/** Localiza o {@link SefazContainer} declarado como campo estático na classe de teste. */
final class SefazContainers {

    private SefazContainers() {
    }

    /** Procura na classe, nas superclasses e nas classes envolventes (testes {@code @Nested}). */
    static Optional<SefazContainer> staticField(Class<?> testClass) {
        for (Class<?> c = testClass; c != null; c = c.getEnclosingClass()) {
            for (Class<?> type = c; type != null && type != Object.class; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) && SefazContainer.class.isAssignableFrom(field.getType())) {
                        try {
                            field.setAccessible(true);
                            Object value = field.get(null);
                            if (value != null) {
                                return Optional.of((SefazContainer) value);
                            }
                        } catch (IllegalAccessException | RuntimeException e) {
                            throw new IllegalStateException("Não foi possível ler o campo " + field, e);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** O container precisa estar no ar antes de entregar URLs; inicia se ninguém iniciou ainda. */
    static SefazContainer started(SefazContainer container) {
        if (!container.isRunning()) {
            container.start();
        }
        return container;
    }
}
