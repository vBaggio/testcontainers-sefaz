# ADR-006: Semântica dos cenários além da especificação

Status: Aceito

## Contexto

A especificação define o catálogo de cenários, mas deixa abertas algumas perguntas que aparecem ao
implementar: a que operações cada cenário se aplica, o que acontece depois de um atraso e o que
fica gravado quando o cliente desiste.

## Decisão

1. **Aplicabilidade.** Cada cenário declara as operações a que se aplica. Uma regra cujo cenário
   não se aplica à operação é ignorada e não consome `times`. Assim
   `when(numero("123")).then(timeoutAfterAuthorize())` não atrasa a consulta de protocolo da mesma
   nota, que casaria pela chave.

   | Cenário | Operações |
   | --- | --- |
   | `TIMEOUT_AFTER_AUTHORIZE`, `TIMEOUT_BEFORE_AUTHORIZE`, `CONNECTION_RESET_AFTER_AUTHORIZE`, `DENIED` | Autorização |
   | `SERVICE_PAUSED` | Status e autorização |
   | `PROCESSING` | Retorno de autorização |
   | `HAPPY_PATH`, `SERVICE_UNAVAILABLE`, `REJECT`, `SLOW` | Todas, inclusive handlers plugados |

2. **Histórico antes do atraso.** Nos cenários de atraso a requisição entra no histórico antes de o
   servidor dormir. O teste vitrine consulta o histórico enquanto o cliente ainda está em timeout.
3. **`TIMEOUT_BEFORE_AUTHORIZE`** responde HTTP 504 com SOAP Fault depois do atraso, se o cliente
   ainda estiver esperando. Nada é gravado.
4. **`CONNECTION_RESET_AFTER_AUTHORIZE`** fecha a conexão sem enviar resposta. O `HttpServer` do JDK
   não expõe o socket, então é um fechamento normal (FIN), não um RST. O cliente vê fim inesperado
   de stream.
5. **`REJECT`** rejeita dentro do `protNFe` (nível nota, padrão) ou no `retEnviNFe` (parâmetro
   `"nivel": "lote"`). Nas outras operações rejeita a resposta inteira. Nota rejeitada fica no
   histórico e em `/__admin/notas`, mas não na base: a consulta devolve 217 e o reenvio autoriza.
6. **Falha no XSD** é um `REJECT` de lote aplicado pelo servidor: 225 na autorização, 215 nas demais
   operações. No histórico, a regra aparece como `xsd-validation`.
7. **Lote com várias notas.** Um cenário por requisição: vale a primeira regra que casar com
   qualquer nota do lote. Duplicidade (204, 205, 539) é checada nota a nota.
8. **Modo assíncrono.** O resultado das notas é decidido no envio. A nota já é consultável pela
   chave antes da consulta do recibo; `PROCESSING` só atrasa a consulta do recibo.
9. **Denegação.** A nota denegada consome a numeração: a consulta devolve o `cStat` da denegação, o
   reenvio da mesma chave devolve 205 e outra chave com a mesma numeração devolve 539.
10. **`times` fluente.** `PUT /__admin/scenarios/{id}` substitui uma regra mantendo sua posição. O
    `SefazAdmin` usa isso para `when(...).then(...).times(n)`: `then` registra a regra e `times`
    a atualiza.

## Consequências

Comportamentos previsíveis e documentados no README. Mudar qualquer item acima é breaking change
pelo ADR-004.
