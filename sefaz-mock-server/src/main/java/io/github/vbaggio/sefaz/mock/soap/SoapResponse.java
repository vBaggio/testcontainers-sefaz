package io.github.vbaggio.sefaz.mock.soap;

import java.util.List;

/**
 * Resposta de um handler: o XML que vai dentro de {@code nfeResultMsg} ou um SOAP Fault.
 *
 * @param cStat código principal, registrado no histórico
 * @param chaves chaves de acesso envolvidas, registradas no histórico
 */
public record SoapResponse(
        int httpStatus,
        String resultXml,
        String faultReason,
        String cStat,
        String xMotivo,
        List<String> chaves) {

    public SoapResponse {
        chaves = chaves == null ? List.of() : List.copyOf(chaves);
    }

    public static SoapResponse ok(String resultXml, String cStat, String xMotivo, List<String> chaves) {
        return new SoapResponse(200, resultXml, null, cStat, xMotivo, chaves);
    }

    public static SoapResponse fault(int httpStatus, String reason) {
        return new SoapResponse(httpStatus, null, reason, null, null, List.of());
    }

    public boolean isFault() {
        return faultReason != null;
    }

    public SoapResponse withChaves(List<String> newChaves) {
        return new SoapResponse(httpStatus, resultXml, faultReason, cStat, xMotivo, newChaves);
    }
}
