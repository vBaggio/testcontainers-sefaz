package io.github.vbaggio.sefaz.mock.handler;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/** Handlers por namespace do {@code nfeDadosMsg}, descobertos via {@link ServiceLoader}. */
public final class HandlerRegistry {

    private final Map<String, OperationHandler> byNamespace;

    private HandlerRegistry(Map<String, OperationHandler> byNamespace) {
        this.byNamespace = byNamespace;
    }

    public static HandlerRegistry load(HandlerEnvironment environment) {
        Map<String, OperationHandler> handlers = new LinkedHashMap<>();
        for (OperationHandler handler : ServiceLoader.load(OperationHandler.class)) {
            handler.init(environment);
            OperationHandler previous = handlers.putIfAbsent(handler.wsdlNamespace(), handler);
            if (previous != null) {
                throw new IllegalStateException("Dois handlers para " + handler.wsdlNamespace() + ": "
                        + previous.getClass().getName() + " e " + handler.getClass().getName());
            }
        }
        return new HandlerRegistry(Map.copyOf(handlers));
    }

    public Optional<OperationHandler> find(String wsdlNamespace) {
        return Optional.ofNullable(byNamespace.get(wsdlNamespace));
    }

    public Collection<OperationHandler> all() {
        return byNamespace.values();
    }
}
