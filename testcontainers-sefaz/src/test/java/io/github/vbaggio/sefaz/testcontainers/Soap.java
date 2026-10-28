package io.github.vbaggio.sefaz.testcontainers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/** Cliente SOAP mínimo dos testes: monta envelopes 1.2 e lê campos da resposta. */
final class Soap {

    static final String CNPJ = "11222333000181";

    private Soap() {
    }

    static HttpClient client(KeyStore trustStore) {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, tmf.getTrustManagers(), null);
            return HttpClient.newBuilder().sslContext(ssl).connectTimeout(Duration.ofSeconds(5)).build();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static HttpResponse<String> post(HttpClient client, String url, String envelope, Duration timeout)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/soap+xml; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(envelope))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    static String status() {
        return envelope(SefazService.STATUS_SERVICO,
                "<consStatServ xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\"><tpAmb>2</tpAmb>"
                        + "<cUF>35</cUF><xServ>STATUS</xServ></consStatServ>");
    }

    static String autorizacao(String chave, boolean sincrono) {
        return envelope(SefazService.AUTORIZACAO,
                "<enviNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\"><idLote>1</idLote>"
                        + "<indSinc>" + (sincrono ? 1 : 0) + "</indSinc><NFe><infNFe Id=\"NFe" + chave + "\" versao=\"4.00\">"
                        + "<ide><tpAmb>2</tpAmb></ide></infNFe></NFe></enviNFe>");
    }

    static String retAutorizacao(String nRec) {
        return envelope(SefazService.RET_AUTORIZACAO,
                "<consReciNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\"><tpAmb>2</tpAmb>"
                        + "<nRec>" + nRec + "</nRec></consReciNFe>");
    }

    static String consulta(String chave) {
        return envelope(SefazService.CONSULTA_PROTOCOLO,
                "<consSitNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\"><tpAmb>2</tpAmb>"
                        + "<xServ>CONSULTAR</xServ><chNFe>" + chave + "</chNFe></consSitNFe>");
    }

    /** Chave fictícia de NF-e (SP, série 1) com DV calculado. */
    static String chave(int numero) {
        String base = "352610" + CNPJ + "55" + "001" + String.format("%09d", numero) + "1" + "12345678";
        int soma = 0;
        int peso = 2;
        for (int i = base.length() - 1; i >= 0; i--) {
            soma += (base.charAt(i) - '0') * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        return base + (resto < 2 ? 0 : 11 - resto);
    }

    static String tag(String xml, String name) {
        Matcher m = Pattern.compile("<" + name + "(\\s[^>]*)?>(.*?)</" + name + ">", Pattern.DOTALL).matcher(xml);
        return m.find() ? m.group(2) : null;
    }

    /** {@code cStat} do protocolo da nota, ou do lote quando não há protocolo. */
    static String cStatNota(String xml) {
        String infProt = tag(xml, "infProt");
        return tag(infProt == null ? xml : infProt, "cStat");
    }

    private static String envelope(SefazService service, String content) {
        return "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\"><soap12:Body>"
                + "<nfeDadosMsg xmlns=\"" + service.wsdlNamespace() + "\">" + content + "</nfeDadosMsg>"
                + "</soap12:Body></soap12:Envelope>";
    }
}
