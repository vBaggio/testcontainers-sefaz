package io.github.vbaggio.sefaz.testcontainers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Requisição recebida pela SEFAZ fake.
 *
 * @param operation {@code STATUS_SERVICO}, {@code AUTORIZACAO}, {@code RET_AUTORIZACAO},
 *     {@code CONSULTA_PROTOCOLO}, ou {@code INVALIDA} para requisições que não chegaram a um handler
 * @param rule id da regra aplicada, ou {@code default} para o cenário padrão do container
 * @param cStat código principal da resposta; no modo síncrono, o da nota
 * @param delivery {@code RESPONDED}, {@code DELAYED} ou {@code CONNECTION_CLOSED}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RecordedRequest(
        long id,
        OffsetDateTime timestamp,
        String operation,
        String path,
        String soapVersion,
        List<String> chaves,
        String scenario,
        String rule,
        String cStat,
        String xMotivo,
        int httpStatus,
        String delivery,
        Long delayMs,
        String clientCertificate) {

    public RecordedRequest {
        chaves = chaves == null ? List.of() : List.copyOf(chaves);
    }

    public boolean is(SefazService service) {
        return service.name().equals(operation);
    }
}
