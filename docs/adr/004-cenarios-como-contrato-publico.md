# ADR-004: Cenários como contrato público versionado

Status: Aceito

## Contexto

O que diferencia a lib é simular falhas, não reproduzir o leiaute.

## Decisão

Cenários têm nome estável, parâmetros documentados e seguem SemVer: renomear ou mudar o
comportamento de um cenário é breaking change. O catálogo vive no enum `Scenario` do servidor e é
espelhado no `Scenario` do módulo Testcontainers.

## Consequências

Testes de usuários não quebram entre versões menores; cenários novos entram como minor.
