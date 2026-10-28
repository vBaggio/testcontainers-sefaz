package io.github.vbaggio.sefaz.spring;

import io.github.vbaggio.sefaz.testcontainers.SefazContainer;
import io.github.vbaggio.sefaz.testcontainers.SefazService;
import java.security.KeyStore;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionDetailsFactory;
import org.springframework.boot.testcontainers.service.connection.ContainerConnectionSource;

/** Cria {@link SefazConnectionDetails} para um {@link SefazContainer} anotado com {@code @ServiceConnection}. */
class SefazContainerConnectionDetailsFactory
        extends ContainerConnectionDetailsFactory<SefazContainer, SefazConnectionDetails> {

    @Override
    protected SefazConnectionDetails getContainerConnectionDetails(ContainerConnectionSource<SefazContainer> source) {
        return new SefazContainerConnectionDetails(source);
    }

    private static final class SefazContainerConnectionDetails extends ContainerConnectionDetails<SefazContainer>
            implements SefazConnectionDetails {

        private SefazContainerConnectionDetails(ContainerConnectionSource<SefazContainer> source) {
            super(source);
        }

        @Override
        public String baseUrl() {
            return getContainer().getBaseUrl();
        }

        @Override
        public String httpBaseUrl() {
            return getContainer().getHttpBaseUrl();
        }

        @Override
        public String url(SefazService service) {
            return getContainer().getUrl(service);
        }

        @Override
        public KeyStore trustStore() {
            return getContainer().getTrustStore();
        }
    }
}
