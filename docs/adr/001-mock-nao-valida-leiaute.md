# ADR-001: O mock não valida o leiaute da nota

Status: Aceito

## Contexto

O leiaute da NF-e muda a cada Nota Técnica, inclusive com a reforma tributária. Embutir XSDs
obrigaria a lançar uma versão da lib a cada mudança.

## Decisão

O corpo da nota é opaco. Só os campos listados no módulo do servidor são lidos: `tpAmb`, `cUF`,
`idLote`, `indSinc`, `infNFe/@Id`, `ide/tpAmb`, o `DigestValue` da assinatura, `nRec` e `chNFe`.
Validação por XSD é opcional, com schemas fornecidos pelo usuário em `SEFAZ_XSD_DIR`.

## Consequências

XML fora do leiaute passa no modo padrão. Em troca, a lib não quebra com mudanças de leiaute e o
usuário valida com a mesma versão de schema que usa em produção.
