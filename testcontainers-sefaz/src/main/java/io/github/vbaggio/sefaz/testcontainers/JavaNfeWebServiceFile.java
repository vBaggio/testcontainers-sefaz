package io.github.vbaggio.sefaz.testcontainers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Gera um arquivo no formato do {@code WebServicesNfe.ini} da Java_NFe com todas as URLs
 * apontando para a SEFAZ fake (ADR-005). Use com {@code ConfiguracoesNfe.setArquivoWebService}.
 */
final class JavaNfeWebServiceFile {

    static final String FILE_NAME = "WebServicesNfe.ini";

    private JavaNfeWebServiceFile() {
    }

    static Path write(Path dir, String baseUrl, Estado... ufs) {
        String content = render(baseUrl, ufs);
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(FILE_NAME);
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar " + FILE_NAME + " em " + dir, e);
        }
    }

    static String render(String baseUrl, Estado... ufs) {
        if (ufs.length == 0) {
            throw new IllegalArgumentException("Informe pelo menos uma UF");
        }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        Set<String> secoes = new LinkedHashSet<>();
        for (Estado uf : new LinkedHashSet<>(Arrays.asList(ufs))) {
            for (String ambiente : new String[] {"H", "P"}) {
                secoes.add("NFe_" + uf.name() + "_" + ambiente);
                secoes.add("NFCe_" + uf.name() + "_" + ambiente);
            }
        }
        // Contingência SVC: a Java_NFe troca de seção conforme a UF (ADR-005).
        for (String ambiente : new String[] {"H", "P"}) {
            secoes.add("NFe_SVRS_" + ambiente);
            secoes.add("NFe_SVC-AN_" + ambiente);
        }

        StringBuilder out = new StringBuilder();
        out.append(";;; Gerado pelo testcontainers-sefaz: todas as URLs apontam para a SEFAZ fake.\n");
        for (String secao : secoes) {
            out.append('[').append(secao).append("]\n");
            out.append("NfeStatusServico_4.00=").append(base).append(SefazService.STATUS_SERVICO.path()).append('\n');
            out.append("NfeAutorizacao_4.00=").append(base).append(SefazService.AUTORIZACAO.path()).append('\n');
            out.append("NFeRetAutorizacao_4.00=").append(base).append(SefazService.RET_AUTORIZACAO.path()).append('\n');
            out.append("NfeConsultaProtocolo_4.00=").append(base).append(SefazService.CONSULTA_PROTOCOLO.path()).append('\n');
            if (secao.startsWith("NFCe_")) {
                out.append("URL-QRCode=").append(base).append("/nfce/qrcode\n");
                out.append("URL-ConsultaNFCe=").append(base).append("/nfce/consulta\n");
            }
            out.append('\n');
        }
        return out.toString();
    }
}
