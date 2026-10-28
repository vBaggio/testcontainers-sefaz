# ADR-002: SOAP escrito à mão, sem JAX-WS nem JAXB

Status: Aceito

## Contexto

Gerar stubs a partir dos WSDLs prende o servidor a uma versão de contrato e aumenta a imagem.

## Decisão

O envelope é lido com DOM e XPath do JDK, com proteção contra XXE ligada. A resposta é montada por
templates de texto em `src/main/resources/templates`, que escapam todo valor vindo da requisição
(`{{valor}}`) e só aceitam XML sem escape para fragmentos montados pelo próprio servidor
(`{{{fragmento}}}`).

## Consequências

Menos dependências e imagem menor: o servidor depende só de Jackson (API admin) e BouncyCastle
(certificado TLS). Exige testes de contrato com fixtures (`ContractTest`) para não regredir o
formato das respostas.
