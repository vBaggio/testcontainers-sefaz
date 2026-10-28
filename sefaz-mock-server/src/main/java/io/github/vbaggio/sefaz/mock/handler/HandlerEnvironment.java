package io.github.vbaggio.sefaz.mock.handler;

import io.github.vbaggio.sefaz.mock.nfe.SefazClock;

/** O que um handler recebe do servidor: relógio e identificação da aplicação nas respostas. */
public record HandlerEnvironment(SefazClock clock, String verAplic) {
}
