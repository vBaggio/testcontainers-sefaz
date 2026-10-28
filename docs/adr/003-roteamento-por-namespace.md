# ADR-003: Roteamento pelo namespace do corpo, não pela URL

Status: Aceito

## Contexto

Cada UF tem URLs diferentes, e bibliotecas montam paths de formas distintas.

## Decisão

A operação é identificada pelo namespace do elemento `nfeDadosMsg`, por exemplo
`http://www.portalfiscal.inf.br/nfe/wsdl/NFeAutorizacao4`. O path da URL é ignorado e só aparece no
histórico.

## Consequências

Qualquer path funciona, o que simplifica a geração de arquivos de URL para Java_NFe e ACBr: todas as
UFs e ambientes apontam para o mesmo container.
