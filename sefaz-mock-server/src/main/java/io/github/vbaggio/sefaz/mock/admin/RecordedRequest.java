package io.github.vbaggio.sefaz.mock.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Uma requisição recebida nas portas SOAP, como aparece em {@code GET /__admin/requests}.
 *
 * @param rule id da regra aplicada, {@code default} para o cenário padrão do container
 * @param delivery como a resposta foi entregue: {@code RESPONDED}, {@code DELAYED},
 *     {@code CONNECTION_CLOSED}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
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
}
