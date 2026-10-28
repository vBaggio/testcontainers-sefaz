package io.github.vbaggio.sefaz.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vbaggio.sefaz.testcontainers.SefazContainer;
import io.github.vbaggio.sefaz.testcontainers.SefazService;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = EmissorTestApplication.class)
@SefazTest(urlProperty = "emissor.sefaz.url", http = true)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SefazSpringBootTest {

    @Container
    @ServiceConnection
    static SefazContainer sefaz = new SefazContainer(System.getProperty("sefaz.image",
            SefazContainer.DEFAULT_IMAGE_NAME.withTag(SefazContainer.DEFAULT_TAG).asCanonicalNameString()));

    @Autowired
    SefazConnectionDetails connectionDetails;

    @Autowired
    EmissorTestApplication.StatusClient statusClient;

    @Test
    @Order(1)
    void serviceConnectionExpoeUrlsETruststore() throws Exception {
        assertThat(connectionDetails.baseUrl()).isEqualTo(sefaz.getBaseUrl());
        assertThat(connectionDetails.httpBaseUrl()).isEqualTo(sefaz.getHttpBaseUrl());
        assertThat(connectionDetails.url(SefazService.AUTORIZACAO)).isEqualTo(sefaz.getUrl(SefazService.AUTORIZACAO));
        assertThat(connectionDetails.trustStore().size()).isEqualTo(1);
    }

    @Test
    @Order(2)
    void urlPropertyRecebeAUrlDoContainer() throws Exception {
        assertThat(statusClient.url()).isEqualTo(sefaz.getHttpBaseUrl());
        assertThat(statusClient.status()).isEqualTo("107");
    }

    @Test
    @Order(3)
    @SefazScenario(numero = "123", scenario = "SERVICE_PAUSED")
    @SefazScenario(operation = "STATUS_SERVICO", scenario = "SLOW", params = "delayMs=10", times = 2)
    void sefazScenarioRegistraAsRegrasAntesDoTeste() throws Exception {
        assertThat(sefaz.admin().rules()).hasSize(2);
        assertThat(sefaz.admin().rules().get(1).times()).isEqualTo(2);
        assertThat(statusClient.status()).isEqualTo("107");
        assertThat(sefaz.admin().requests().get(0).scenario()).isEqualTo("SLOW");
    }

    @Test
    @Order(4)
    @SefazScenario(scenario = "SERVICE_PAUSED")
    void entraEmContingenciaQuandoSefazParalisa() throws Exception {
        assertThat(statusClient.status()).isEqualTo("108");
    }

    @Test
    @Order(5)
    void estadoFoiResetadoDepoisDoTesteAnterior() {
        assertThat(sefaz.admin().rules()).isEmpty();
        assertThat(sefaz.admin().requests()).isEmpty();
    }
}
