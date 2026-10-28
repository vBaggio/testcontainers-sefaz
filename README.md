# testcontainers-sefaz

[![build](https://github.com/vBaggio/testcontainers-sefaz/actions/workflows/build.yml/badge.svg)](https://github.com/vBaggio/testcontainers-sefaz/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.vbaggio/testcontainers-sefaz)](https://central.sonatype.com/artifact/io.github.vbaggio/testcontainers-sefaz)
[![Licença](https://img.shields.io/badge/licen%C3%A7a-Apache%202.0-blue)](LICENSE)

Uma SEFAZ fake para testar emissão de NF-e e NFC-e, empacotada como imagem Docker e módulo
Testcontainers.

Testar emissão hoje exige certificado A1 e a homologação da SEFAZ, que fica instável, lenta e não
permite simular falhas. Não há como escrever um teste automatizado para "a SEFAZ deu timeout depois
de autorizar a nota". O testcontainers-sefaz fala o protocolo SOAP dos web services da NF-e 4.00,
guarda o estado das notas e responde conforme cenários que o seu teste escolhe.

## O teste que motiva o projeto

O emissor manda a nota, a SEFAZ autoriza, mas a resposta não chega. Reenviar gera duplicidade; o
certo é consultar o protocolo. Este teste prova que o emissor faz isso, usando a Java_NFe de verdade
(do [exemplo](examples/java-nfe-example)):

```java
@Testcontainers
class EmissaoJavaNfeTest {

    @Container
    static SefazContainer sefaz = new SefazContainer();

    @BeforeEach
    void limpa() { sefaz.admin().reset(); }

    @Test
    void naoReenviaNotaJaAutorizadaAposTimeout() throws Exception {
        sefaz.admin()
             .when(Match.numero("123"))
             .then(Scenario.timeoutAfterAuthorize(Duration.ofSeconds(2)))
             .times(1);

        var resultado = emissor.emitir(NotaFicticia.numero(123));

        assertThat(resultado.status()).isEqualTo(Status.AUTORIZADA);
        assertThat(sefaz.admin().requests(AUTORIZACAO)).hasSize(1);
        assertThat(sefaz.admin().requests(CONSULTA_PROTOCOLO)).hasSize(1);
    }
}
```

A SEFAZ fake autoriza e grava a nota, segura a resposta por 2 s, e o cliente (com timeout de 1 s)
desiste. A consulta seguinte devolve 100 com o protocolo original. A regra vale uma vez (`times(1)`),
então o caso "falha na primeira tentativa, autoriza na segunda" também é um teste de três linhas.

## Instalação

```kotlin
// build.gradle.kts
testImplementation("io.github.vbaggio:testcontainers-sefaz:0.1.0")
// Opcional, para Spring Boot 3.x ou 4.x:
testImplementation("io.github.vbaggio:testcontainers-sefaz-spring-boot:0.1.0")
```

```xml
<dependency>
  <groupId>io.github.vbaggio</groupId>
  <artifactId>testcontainers-sefaz</artifactId>
  <version>0.1.0</version>
  <scope>test</scope>
</dependency>
```

Requisitos: Java 17+ e Docker. `new SefazContainer()` usa a imagem
`ghcr.io/vbaggio/sefaz-mock-server` com a tag igual à versão da biblioteca.

## O que a SEFAZ fake atende

NF-e modelo 55 e NFC-e modelo 65, leiaute 4.00:

| Web service | Operação | `SefazService` |
| --- | --- | --- |
| NFeStatusServico4 | `nfeStatusServicoNF` | `STATUS_SERVICO` |
| NFeAutorizacao4 | `nfeAutorizacaoLote`, síncrono (`indSinc=1`) e assíncrono | `AUTORIZACAO` |
| NFeRetAutorizacao4 | `nfeRetAutorizacaoLote` | `RET_AUTORIZACAO` |
| NFeConsultaProtocolo4 | `nfeConsultaNF` | `CONSULTA_PROTOCOLO` |

Comportamento padrão: status 107; autorização 100 (síncrona: lote 104 com `protNFe`; assíncrona:
lote 103 com recibo, e o retorno devolve 104); consulta 100 para nota conhecida e 217 para
desconhecida; 204 para a mesma chave já autorizada e 539 para a mesma numeração com outra chave. O
protocolo tem 15 dígitos, sequencial por container, e o `digVal` copia o `DigestValue` da nota.

A operação é identificada pelo namespace do `nfeDadosMsg`, não pela URL: qualquer path funciona
([ADR-003](docs/adr/003-roteamento-por-namespace.md)). SOAP 1.2 (`application/soap+xml`) e 1.1
(`text/xml`).

## Cenários

| Cenário | Comportamento | Parâmetros | Operações | O que você testa |
| --- | --- | --- | --- | --- |
| `HAPPY_PATH` | Autoriza normalmente (padrão) | | Todas | Fluxo feliz |
| `TIMEOUT_AFTER_AUTHORIZE` | Autoriza e grava, depois segura a resposta | `delayMs` (60000) | Autorização | Consultar antes de reenviar; a consulta devolve 100 |
| `TIMEOUT_BEFORE_AUTHORIZE` | Segura a resposta, não grava e responde 504 | `delayMs` (60000) | Autorização | Consulta devolve 217; reenviar é seguro |
| `CONNECTION_RESET_AFTER_AUTHORIZE` | Autoriza, grava e fecha a conexão sem responder | | Autorização | Mesmo tratamento do timeout, com outro erro de I/O |
| `SERVICE_PAUSED` | Devolve 108 | | Status e autorização | Entrada em contingência |
| `SERVICE_UNAVAILABLE` | HTTP 503 com SOAP Fault | | Todas | Contingência por falha de transporte |
| `PROCESSING` | Retorno do recibo devolve 105 N vezes, depois 104 | `attempts` (3) | Retorno de autorização | Polling do recibo com backoff |
| `DENIED` | Uso denegado, nota gravada como denegada | `cStat` (301) | Autorização | Denegação, que consome a numeração |
| `REJECT` | Rejeição com o código e o texto dados | `cStat`, `xMotivo`, `nivel` (`nota` ou `lote`) | Todas | Qualquer rejeição específica |
| `SLOW` | Responde normalmente depois do atraso | `delayMs` (3000) | Todas | Timeouts e métricas de latência |

Os nomes e parâmetros são contrato público: mudá-los é breaking change
([ADR-004](docs/adr/004-cenarios-como-contrato-publico.md)). Os detalhes que a especificação deixou
em aberto estão no [ADR-006](docs/adr/006-semantica-dos-cenarios.md).

### Como um cenário é escolhido

A primeira regra que casar vence, nesta ordem:

1. Regra que casa com a nota: por chave exata ou por campos da chave (`cnpj`, `serie`, `numero`).
2. Regra que casa só com a operação (ou com qualquer requisição).
3. Cenário padrão do container (`withDefaultScenario`).

Uma regra cujo cenário não se aplica à operação é ignorada e não gasta `times`. Por isso
`when(Match.numero("123")).then(timeoutAfterAuthorize(...))` atrasa a autorização da nota 123, mas
não a consulta de protocolo dela.

```java
sefaz.admin().when(Match.serie("1").numero("123")).then(Scenario.denied("302")).times(1);
sefaz.admin().when(Match.operation(RET_AUTORIZACAO)).then(Scenario.processing(2));
sefaz.admin().when(Match.any()).then(Scenario.servicePaused());
sefaz.admin().when(Match.chave(chave)).then(Scenario.reject("778", "Rejeição: ..."));
```

O histórico registra a regra aplicada em cada requisição, para depurar o próprio teste:

```java
RecordedRequest r = sefaz.admin().requests(AUTORIZACAO).get(0);
r.scenario();   // TIMEOUT_AFTER_AUTHORIZE
r.rule();       // rule-1, ou "default"
r.cStat();      // 100
r.delivery();   // DELAYED
```

## SefazContainer

```java
SefazContainer sefaz = new SefazContainer()
        .withDefaultScenario(Scenario.slow(Duration.ofMillis(200)))
        .withClientAuth(ClientAuth.NEED)            // NONE, WANT (padrão), NEED
        .withXsdDirectory(Path.of("schemas"));      // validação opcional, ADR-001

sefaz.getBaseUrl();                          // https://host:porta
sefaz.getHttpBaseUrl();                      // http://host:porta
sefaz.getUrl(SefazService.AUTORIZACAO);      // https://host:porta/ws/NFeAutorizacao4
sefaz.getTrustStore();                       // KeyStore com o certificado autoassinado
sefaz.writeTrustStore(dir, "changeit".toCharArray());
sefaz.admin();                               // regras, requisições, notas, reset
```

O certificado TLS é gerado na inicialização com o host do Docker e `localhost` nos SANs. Qualquer
certificado de cliente é aceito, e o subject fica no histórico.

## Com a Java_NFe

A Java_NFe aceita um arquivo de URLs próprio. O container gera esse arquivo apontando para si mesmo:

```java
Path ini = sefaz.writeJavaNfeWebServiceFile(dir, sefaz.getHttpBaseUrl(), Estado.SP);

ConfiguracoesNfe config = ConfiguracoesNfe.criarConfiguracoes(
        EstadosEnum.SP, AmbienteEnum.HOMOLOGACAO, certificado, pastaSchemas);
config.setArquivoWebService(ini.toString());
```

- Com `getHttpBaseUrl()` (porta 8080), o TLS sai do caminho. Sem esse argumento, o arquivo usa
  HTTPS; nesse caso passe o truststore do container como cacert, com a senha `changeit`, e entregue
  um `InputStream` novo a cada operação, porque a Java_NFe relê o cacert a cada chamada.
- O certificado A1 pode ser fictício: [`CertificadoA1Ficticio`](examples/java-nfe-example/src/test/java/io/github/vbaggio/sefaz/examples/javanfe/CertificadoA1Ficticio.java)
  gera um PKCS12 autoassinado com o CNPJ onde a Java_Certificado procura.

Os detalhes estão no [ADR-005](docs/adr/005-verificacao-java-nfe-e-baselines.md), e o exemplo
completo em [`examples/java-nfe-example`](examples/java-nfe-example).

## Com Spring Boot

```java
@SpringBootTest
@SefazTest(urlProperty = "emissor.sefaz.url")
@Testcontainers
class EmissaoServiceIT {

    @Container
    @ServiceConnection
    static SefazContainer sefaz = new SefazContainer();

    @Test
    @SefazScenario(numero = "123", scenario = "SERVICE_PAUSED")
    void entraEmContingenciaQuandoSefazParalisa() { ... }
}
```

- `@SefazTest(urlProperty = ...)` é o caminho mais simples: não existe propriedade padrão de URL da
  SEFAZ, então você diz qual propriedade da sua aplicação recebe a URL. Use `http = true` para a URL
  HTTP.
- `@ServiceConnection` cria um bean `SefazConnectionDetails` (`baseUrl()`, `url(SefazService)`,
  `trustStore()`). Ele só tem efeito se a sua aplicação ler esse bean.
- `@SefazScenario` é repetível e aceita `operation`, `chave`, `cnpj`, `serie`, `numero`, `params`
  (`"delayMs=2000"`) e `times`. Depois de cada teste o estado da SEFAZ fake é resetado.
- O módulo não puxa Spring: ele espera `spring-boot-testcontainers` da sua aplicação. Testado com
  Spring Boot 3.5 e 4.1.

## API admin (porta 8081)

| Método e rota | Função |
| --- | --- |
| `GET /__admin/health` | Readiness |
| `GET /__admin/requests[?operation=AUTORIZACAO]` | Histórico: operação, chaves, cenário, regra, `cStat`, horário |
| `GET /__admin/notas` e `GET /__admin/notas/{chave}` | Estado das notas, com o XML recebido |
| `GET /__admin/scenarios` | Regras ativas e quantas aplicações restam |
| `POST /__admin/scenarios` | Registra uma regra |
| `PUT /__admin/scenarios/{id}` e `DELETE /__admin/scenarios/{id}` | Substitui ou remove uma regra |
| `DELETE /__admin/state` | Limpa notas, regras e histórico |
| `GET /__admin/tls/truststore` e `GET /__admin/tls/certificate` | Truststore PKCS12 (`changeit`) e certificado PEM |

```json
{
  "match": { "operation": "AUTORIZACAO", "serie": "1", "numero": "123" },
  "scenario": "TIMEOUT_AFTER_AUTHORIZE",
  "params": { "delayMs": 30000 },
  "times": 1
}
```

A imagem também roda sozinha, para quem não usa Java:

```sh
docker run -p 8443:8443 -p 8080:8080 -p 8081:8081 ghcr.io/vbaggio/sefaz-mock-server:0.1.0
```

| Variável | Uso |
| --- | --- |
| `SEFAZ_DEFAULT_SCENARIO`, `SEFAZ_DEFAULT_SCENARIO_PARAMS` | Cenário padrão e parâmetros em JSON |
| `SEFAZ_TLS_SANS` | Nomes e IPs extras do certificado, separados por vírgula |
| `SEFAZ_CLIENT_AUTH` | `none`, `want` (padrão) ou `need` |
| `SEFAZ_XSD_DIR` | Diretório com XSDs para validar o conteúdo; falha devolve 225 (autorização) ou 215 |

## Limitações

- **Não valida o leiaute.** XML fora do leiaute passa no modo padrão; a validação por XSD é opcional,
  com os schemas que você usa em produção ([ADR-001](docs/adr/001-mock-nao-valida-leiaute.md)).
- **Não valida assinatura nem cadeia ICP-Brasil**, não calcula tributos e não aplica regras de
  negócio da SEFAZ.
- **Não serve para certificação** de software emissor junto à SEFAZ.
- **Fora do MVP:** eventos (cancelamento, carta de correção, reforma tributária), inutilização,
  distribuição DF-e, MDF-e e CT-e. Handlers são carregados via `ServiceLoader`, então um serviço novo
  é uma classe nova mais uma linha em `META-INF/services`.
- **Estado em memória.** Reiniciar o container apaga notas e protocolos.
- **Textos de retorno.** Os `xMotivo` seguem o MOC 7.0, mas parte deles ainda precisa ser conferida
  no documento vigente ([ADR-005](docs/adr/005-verificacao-java-nfe-e-baselines.md)).

## Alternativas

- **Homologação da SEFAZ.** É a SEFAZ de verdade, com as regras de negócio que o mock não tem, e
  continua necessária antes de ir para produção. Exige certificado A1 real e cadastro do emissor,
  fica fora do ar com frequência, é lenta e não deixa forçar falhas: não dá para pedir um timeout
  depois da autorização.
- **Mocks HTTP genéricos (WireMock, MockServer).** Servem para respostas fixas, mas você escreve e
  mantém os XMLs à mão, e eles não têm estado: duplicidade, consulta depois de timeout e retorno de
  recibo viram roteiros frágeis.
- **Mockar a biblioteca de NF-e no teste unitário.** Rápido, mas não exercita SOAP, TLS, timeouts
  do cliente HTTP nem o parsing das respostas, que é onde os erros de integração aparecem.
- Não encontramos, em outubro de 2026, um mock de SEFAZ dedicado e mantido com estado e cenários
  de falha. Se existir um, abra uma issue que a gente lista aqui.

## Decisões de arquitetura

As decisões estão em [`docs/adr`](docs/adr/README.md): o mock não valida o leiaute, SOAP escrito à
mão, roteamento pelo namespace, cenários como contrato público, as respostas da verificação inicial
sobre a Java_NFe, a semântica dos cenários e o build.

## Construindo localmente

Requisitos: JDK 21 e Docker.

```sh
./gradlew build                                   # tudo, com a imagem gerada por Jib no daemon local
./gradlew :sefaz-mock-server:jibDockerBuild       # só a imagem
./gradlew :testcontainers-sefaz-spring-boot:test -PspringBootVersion=4.1.2
./gradlew :sefaz-mock-server:test -PupdateFixtures   # regrava as fixtures de contrato; revise o diff
```

Os testes de integração rodam contra a imagem construída no mesmo build, nunca contra uma tag
publicada.

## Licença

[Apache 2.0](LICENSE).
