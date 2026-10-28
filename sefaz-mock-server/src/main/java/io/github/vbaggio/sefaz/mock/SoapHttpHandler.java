package io.github.vbaggio.sefaz.mock;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.github.vbaggio.sefaz.mock.admin.RecordedRequest;
import io.github.vbaggio.sefaz.mock.admin.RequestHistory;
import io.github.vbaggio.sefaz.mock.handler.HandlerRegistry;
import io.github.vbaggio.sefaz.mock.handler.OperationHandler;
import io.github.vbaggio.sefaz.mock.nfe.ChaveAcesso;
import io.github.vbaggio.sefaz.mock.nfe.SefazClock;
import io.github.vbaggio.sefaz.mock.scenario.Scenario;
import io.github.vbaggio.sefaz.mock.scenario.ScenarioContext;
import io.github.vbaggio.sefaz.mock.scenario.ScenarioRegistry;
import io.github.vbaggio.sefaz.mock.soap.SoapEnvelopes;
import io.github.vbaggio.sefaz.mock.soap.SoapEnvelopes.InvalidEnvelopeException;
import io.github.vbaggio.sefaz.mock.soap.SoapRequest;
import io.github.vbaggio.sefaz.mock.soap.SoapResponse;
import io.github.vbaggio.sefaz.mock.soap.SoapVersion;
import io.github.vbaggio.sefaz.mock.store.NotaStore;
import io.github.vbaggio.sefaz.mock.tls.TlsSupport;
import io.github.vbaggio.sefaz.mock.xsd.XsdValidator;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pipeline de uma requisição SOAP: content-type, envelope, roteamento pelo namespace, XSD
 * opcional, cenário, handler, histórico e entrega da resposta.
 */
final class SoapHttpHandler implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(SoapHttpHandler.class.getName());
    static final String OPERACAO_INVALIDA = "INVALIDA";

    private final HandlerRegistry handlers;
    private final ScenarioRegistry scenarios;
    private final NotaStore store;
    private final RequestHistory history;
    private final XsdValidator xsd;
    private final SefazClock clock;

    SoapHttpHandler(HandlerRegistry handlers, ScenarioRegistry scenarios, NotaStore store, RequestHistory history,
            XsdValidator xsd, SefazClock clock) {
        this.handlers = handlers;
        this.scenarios = scenarios;
        this.store = store;
        this.history = history;
        this.xsd = xsd;
        this.clock = clock;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            process(exchange);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Erro ao processar requisição SOAP", e);
        }
    }

    private void process(HttpExchange exchange) throws IOException, InterruptedException {
        String path = exchange.getRequestURI().getPath();
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Use POST com um envelope SOAP");
            return;
        }
        Optional<SoapVersion> version = SoapVersion.fromContentType(exchange.getRequestHeaders().getFirst("Content-Type"));
        if (version.isEmpty()) {
            record(path, null, OPERACAO_INVALIDA, List.of(), null, null, 415, "RESPONDED", null, null);
            sendText(exchange, 415, "Content-type deve ser application/soap+xml (SOAP 1.2) ou text/xml (SOAP 1.1)");
            return;
        }
        String clientCertificate = TlsSupport.clientCertificate(exchange);

        SoapRequest request;
        try (InputStream body = exchange.getRequestBody()) {
            request = SoapEnvelopes.read(version.get(), path, body, clientCertificate);
        } catch (InvalidEnvelopeException e) {
            sendFault(exchange, version.get(), path, OPERACAO_INVALIDA, 500, e.getMessage(), clientCertificate);
            return;
        }
        Optional<OperationHandler> handler = handlers.find(request.wsdlNamespace());
        if (handler.isEmpty()) {
            sendFault(exchange, version.get(), path, OPERACAO_INVALIDA, 500,
                    "Serviço não suportado: " + request.wsdlNamespace(), clientCertificate);
            return;
        }
        dispatch(exchange, request, handler.get());
    }

    private void dispatch(HttpExchange exchange, SoapRequest request, OperationHandler handler)
            throws IOException, InterruptedException {
        String operation = handler.operation();
        List<ChaveAcesso> chaves = handler.chaves(request, store);
        Optional<String> schemaError = xsd.validate(request.content(), handler.xsdFileName());
        ScenarioContext scenario = schemaError.isPresent()
                ? new ScenarioContext(Scenario.REJECT, Map.of("cStat", handler.schemaFailureCStat(), "nivel", "lote"),
                        "xsd-validation")
                : scenarios.resolve(operation, chaves);
        schemaError.ifPresent(error -> LOG.info(() -> operation + " falhou no XSD: " + error));
        List<String> chavesTexto = chaves.stream().map(ChaveAcesso::valor).toList();

        switch (scenario.scenario()) {
            case SERVICE_UNAVAILABLE -> {
                SoapResponse fault = SoapResponse.fault(503, "Serviço indisponível (cenário SERVICE_UNAVAILABLE)");
                record(request, operation, chavesTexto, scenario, fault, "RESPONDED", null);
                send(exchange, request, fault);
            }
            case TIMEOUT_BEFORE_AUTHORIZE -> {
                // Nada é gravado: depois do atraso o cliente recebe 504, se ainda estiver esperando.
                SoapResponse fault = SoapResponse.fault(504, "Tempo de processamento esgotado (cenário TIMEOUT_BEFORE_AUTHORIZE)");
                record(request, operation, chavesTexto, scenario, fault, "DELAYED", scenario.delayMs());
                Thread.sleep(scenario.delayMs());
                send(exchange, request, fault);
            }
            case SLOW -> {
                Thread.sleep(scenario.delayMs());
                SoapResponse response = handler.handle(request, scenario, store);
                record(request, operation, chavesTexto, scenario, response, "DELAYED", scenario.delayMs());
                send(exchange, request, response);
            }
            case TIMEOUT_AFTER_AUTHORIZE -> {
                // Grava antes de esperar: o teste consulta o histórico enquanto o cliente está em timeout.
                SoapResponse response = handler.handle(request, scenario, store);
                record(request, operation, chavesTexto, scenario, response, "DELAYED", scenario.delayMs());
                Thread.sleep(scenario.delayMs());
                send(exchange, request, response);
            }
            case CONNECTION_RESET_AFTER_AUTHORIZE -> {
                SoapResponse response = handler.handle(request, scenario, store);
                record(request, operation, chavesTexto, scenario, response, "CONNECTION_CLOSED", null);
                // Fechar a troca antes de enviar cabeçalhos faz o servidor do JDK fechar o socket.
                exchange.close();
            }
            default -> {
                SoapResponse response = handler.handle(request, scenario, store);
                record(request, operation, chavesTexto, scenario, response, "RESPONDED", null);
                send(exchange, request, response);
            }
        }
    }

    private void record(SoapRequest request, String operation, List<String> chaves, ScenarioContext scenario,
            SoapResponse response, String delivery, Long delayMs) {
        List<String> registradas = response.chaves().isEmpty() ? chaves : response.chaves();
        record(request.path(), request.version(), operation, registradas, scenario, response, response.httpStatus(),
                delivery, delayMs, request.clientCertificate());
    }

    private void record(String path, SoapVersion version, String operation, List<String> chaves,
            ScenarioContext scenario, SoapResponse response, int httpStatus, String delivery, Long delayMs,
            String clientCertificate) {
        RecordedRequest entry = new RecordedRequest(
                history.nextId(),
                clock.now(),
                operation,
                path,
                version == null ? null : version.name(),
                chaves,
                scenario == null ? null : scenario.scenario().name(),
                scenario == null ? null : scenario.source(),
                response == null ? null : response.cStat(),
                response == null ? null : response.isFault() ? response.faultReason() : response.xMotivo(),
                httpStatus,
                delivery,
                delayMs,
                clientCertificate);
        history.add(entry);
        LOG.info(() -> String.format("%s %s chaves=%s cenario=%s regra=%s cStat=%s http=%d %s",
                operation, path, chaves, entry.scenario(), entry.rule(), entry.cStat(), httpStatus, delivery));
    }

    private void sendFault(HttpExchange exchange, SoapVersion version, String path, String operation, int status,
            String reason, String clientCertificate) throws IOException {
        SoapResponse fault = SoapResponse.fault(status, reason);
        record(path, version, operation, List.of(), null, fault, status, "RESPONDED", null, clientCertificate);
        byte[] body = SoapEnvelopes.fault(version, reason).getBytes(StandardCharsets.UTF_8);
        send(exchange, status, version.contentType(), body);
    }

    private static void send(HttpExchange exchange, SoapRequest request, SoapResponse response) throws IOException {
        String xml = response.isFault()
                ? SoapEnvelopes.fault(request.version(), response.faultReason())
                : SoapEnvelopes.result(request.version(), request.wsdlNamespace(), response.resultXml());
        send(exchange, response.httpStatus(), request.version().contentType(), xml.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendText(HttpExchange exchange, int status, String text) throws IOException {
        send(exchange, status, "text/plain; charset=utf-8", text.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        try {
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        } catch (IOException e) {
            // Cliente desistiu (timeout do lado dele): esperado nos cenários de atraso.
            LOG.fine(() -> "Cliente fechou a conexão antes da resposta: " + e.getMessage());
        }
    }
}
