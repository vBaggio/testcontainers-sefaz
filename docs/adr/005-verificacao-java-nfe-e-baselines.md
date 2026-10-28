# ADR-005: Respostas da etapa 0 (Java_NFe, baselines e cStat)

Status: Aceito, com uma pendência (conferência dos textos no MOC)

## Contexto

A especificação pede cinco verificações sobre a Java_NFe, a conferência dos `cStat` no Manual de
Orientação do Contribuinte (MOC) e a confirmação do baseline de Java do Testcontainers antes de
codar a etapa 4. As respostas abaixo foram tiradas do código-fonte, não de documentação secundária:
Java_NFe 4.2.0 (commit `33bfa6f`, 19/10/2026) e Java_Certificado 3.17, ambos de
github.com/Samuel-Oliveira.

## Decisão

### 1. Formato do `WebServicesNfe.ini`

- Seções `[<Documento>_<UF>_<Ambiente>]`: documento `NFe` ou `NFCe`, UF em sigla, ambiente `H`
  (homologação) ou `P` (produção). Exemplo: `[NFCe_SP_H]`.
- Chaves por serviço, comparadas sem diferenciar maiúsculas: `NfeStatusServico_4.00`,
  `NfeAutorizacao_4.00`, `NFeRetAutorizacao_4.00`, `NfeConsultaProtocolo_4.00`,
  `NfeInutilizacao_4.00`, `RecepcaoEvento_4.00`, `NfeConsultaCadastro_4.00`. Seções de NFC-e têm
  ainda `URL-QRCode` e `URL-ConsultaNFCe`.
- `Usar=<seção>` redireciona a UF para outra seção (por exemplo `Usar=NFe_SVRS_H`).
- Contingência SVC usa `NFe_SVC-AN_<amb>` ou `NFe_SVRS_<amb>`, conforme a UF.
- O arquivo customizado entra por `ConfiguracoesNfe.setArquivoWebService(caminho)`
  (`WebServiceUtil.getUrl`).

O gerador `SefazContainer.writeJavaNfeWebServiceFile` escreve as seções `H` e `P` de NF-e e NFC-e
de cada UF pedida, mais as seções de SVC, todas apontando para o container.

### 2. Truststore

`SocketFactoryDinamico` (Java_Certificado) carrega o cacert como `KeyStore.getDefaultType()`
(PKCS12 desde o Java 9) com a senha fixa `changeit`. `ConfiguracoesNfe.setCacert(InputStream)`
aceita outro cacert. Basta, portanto, `SefazContainer.writeTrustStore(dir, "changeit")`.

Armadilha: todo método de `Nfe` chama `ConfiguracoesUtil.iniciaConfiguracoes`, que lê o
`InputStream` de novo. Um stream único funciona só na primeira chamada; o exemplo entrega um
`ByteArrayInputStream` novo antes de cada operação.

### 3. URL `http://` no arquivo customizado

Aceita. O transporte do Axis2 é escolhido pelo esquema da URL, e o certificado só é registrado como
protocolo `https`. O exemplo usa a porta 8080 e o TLS sai do caminho; o teste HTTPS fica no módulo
Testcontainers. Verificado em `examples/java-nfe-example`.

### 4. Dados exigidos do certificado de cliente

- Validade por data (`Certificado.isValido`), conferida em `criarConfiguracoes`.
- CNPJ lido do Subject Alternative Name (2.5.29.17), `otherName` com OID `2.16.76.1.3.3`
  (`DocumentoUtil`). O subject não é usado.
- Os 8 primeiros dígitos do CNPJ do certificado precisam bater com o do emitente quando
  `validacaoDocumento` está ligado (padrão).
- Chave privada RSA, usada para assinar a nota.

O utilitário `CertificadoA1Ficticio` do exemplo gera um PKCS12 autoassinado com esse `otherName`.

### 5. Versão e baseline

Java_NFe 4.2.0 é a versão atual em `br.com.swconsultoria:java-nfe`, compilada para Java 8. O
exemplo não compila com `--release 8`: ele depende de `testcontainers-sefaz`, que é Java 17. O
exemplo fica em Java 17; a Java_NFe continua sendo usada como biblioteca Java 8.

### Baseline do Testcontainers

Testcontainers 2.0.5 (atual) é distribuído em bytecode Java 8 (class file 52). Fixar 17 nos
módulos cliente é escolha nossa, não exigência dele: Spring Boot 3 exige 17 e o cliente admin usa
`java.net.http.HttpClient`.

### `cStat` e `xMotivo`

O portal do MOC (nfe.fazenda.gov.br) não estava acessível no ambiente em que a implementação foi
feita. Os códigos 100, 103, 104, 105, 107, 110, 204, 301, 302, 303 foram conferidos contra o
`StatusEnum` da Java_NFe. Os textos de 106, 108, 205, 215, 217, 225 e 539 seguem a tabela do MOC 7.0
como conhecida, sem conferência no documento.

## Consequências

- Etapa 4 liberada.
- Pendência antes da `v0.1.0`: conferir os textos de `Motivo.java` no MOC vigente e atualizar este
  ADR. A mudança de um texto não muda o contrato dos cenários (ADR-004), só o `xMotivo`.
