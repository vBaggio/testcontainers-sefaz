# ADR-007: Build Gradle 9, coordenadas e publicação

Status: Aceito

## Contexto

A especificação pede Gradle Kotlin DSL, version catalog, convention plugin e publicação no Maven
Central pelo plugin `com.vanniktech.maven.publish`. A versão atual do plugin (0.37) é compilada com
Kotlin 2.2, que o Gradle 8 não carrega.

## Decisão

- Gradle wrapper 9.8.2. Convention plugins em `build-logic/`: `sefaz.java-conventions`,
  `sefaz.publishing-conventions` e `sefaz.docker-image` (Jib).
- Um único JDK 21 no build. Os módulos cliente compilam com `--release 17`; o servidor, com
  `--release 21`. Sem toolchains, para não baixar JDKs no CI.
- `groupId` `io.github.vbaggio`, a partir do dono do repositório no GitHub (`vBaggio`). A
  especificação pede confirmação do dono antes da primeira publicação.
- Pacote base `io.github.vbaggio.sefaz`.
- Imagem `ghcr.io/vbaggio/sefaz-mock-server`, com a mesma versão dos artefatos. A imagem base é
  configurável (`-PjibBaseImage`), e a lista de plataformas também (`-PjibPlatforms`); o release
  publica `linux/amd64,linux/arm64`.
- Assinatura GPG só quando `signingInMemoryKey` está presente, para que builds locais publiquem
  em `mavenLocal` sem chave.

## Consequências

Quem clona o projeto precisa só de um JDK 21 e Docker. Trocar o `groupId` depois da primeira
publicação é caro; por isso a confirmação fica como item explícito do release.
