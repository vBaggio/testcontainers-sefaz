package io.github.vbaggio.sefaz.spring;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

/** Aplicação mínima que lê a URL da SEFAZ de uma propriedade própria, como um emissor real. */
@SpringBootConfiguration
@EnableAutoConfiguration
class EmissorTestApplication {

    @Bean
    StatusClient statusClient(@Value("${emissor.sefaz.url}") String url) {
        return new StatusClient(url);
    }

    static final class StatusClient {
        private static final Pattern CSTAT = Pattern.compile("<cStat>(\\d+)</cStat>");
        private final String url;
        private final HttpClient http = HttpClient.newHttpClient();

        StatusClient(String url) {
            this.url = url;
        }

        String url() {
            return url;
        }

        String status() throws IOException, InterruptedException {
            String envelope = "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\"><soap12:Body>"
                    + "<nfeDadosMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeStatusServico4\">"
                    + "<consStatServ xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\"><tpAmb>2</tpAmb>"
                    + "<cUF>35</cUF><xServ>STATUS</xServ></consStatServ></nfeDadosMsg></soap12:Body></soap12:Envelope>";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url + "/ws/NFeStatusServico4"))
                    .header("Content-Type", "application/soap+xml; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(envelope))
                    .build();
            Matcher m = CSTAT.matcher(http.send(request, HttpResponse.BodyHandlers.ofString()).body());
            return m.find() ? m.group(1) : null;
        }
    }
}
