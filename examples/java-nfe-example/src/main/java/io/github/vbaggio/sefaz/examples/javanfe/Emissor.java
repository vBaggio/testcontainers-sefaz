package io.github.vbaggio.sefaz.examples.javanfe;

import br.com.swconsultoria.nfe.Nfe;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import br.com.swconsultoria.nfe.dom.enuns.DocumentoEnum;
import br.com.swconsultoria.nfe.exception.NfeException;
import br.com.swconsultoria.nfe.schemas.TEnviNFe;
import br.com.swconsultoria.nfe.schemas.TProtNFe;
import br.com.swconsultoria.nfe.schemas.TRetConsSitNFe;
import br.com.swconsultoria.nfe.schemas.TRetConsStatServ;
import br.com.swconsultoria.nfe.schemas.TRetEnviNFe;
import io.github.vbaggio.sefaz.examples.javanfe.ResultadoEmissao.Status;
import java.io.IOException;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Emissor de NF-e modelo 55 no modo síncrono, com a regra que a SEFAZ fake permite testar: se a
 * comunicação falhar depois do envio, consulte o protocolo antes de reenviar.
 *
 * <p>As configurações vêm de um {@link Supplier} porque a Java_NFe relê o cacert a cada operação
 * (ADR-005).
 */
public final class Emissor {

    static final int MAX_ENVIOS = 2;
    private static final Set<String> DENEGACOES = Set.of("110", "301", "302", "303");
    private static final Set<String> SERVICO_PARADO = Set.of("108", "109");

    private final Supplier<ConfiguracoesNfe> configuracoes;

    public Emissor(Supplier<ConfiguracoesNfe> configuracoes) {
        this.configuracoes = configuracoes;
    }

    public ResultadoEmissao emitir(TEnviNFe lote) throws NfeException {
        String chave = lote.getNFe().get(0).getInfNFe().getId().substring(3);

        try {
            TRetConsStatServ status = Nfe.statusServico(configuracoes.get(), DocumentoEnum.NFE);
            if (!"107".equals(status.getCStat())) {
                return new ResultadoEmissao(Status.CONTINGENCIA, chave, status.getCStat(), status.getXMotivo(), null, 0,
                        false);
            }
        } catch (NfeException e) {
            if (!falhaDeComunicacao(e)) {
                throw e;
            }
            return new ResultadoEmissao(Status.CONTINGENCIA, chave, null, e.getMessage(), null, 0, false);
        }

        TEnviNFe assinado = Nfe.montaNfe(configuracoes.get(), lote, false);
        for (int envio = 1; envio <= MAX_ENVIOS; envio++) {
            try {
                TRetEnviNFe retorno = Nfe.enviarNfe(configuracoes.get(), assinado, DocumentoEnum.NFE);
                if (SERVICO_PARADO.contains(retorno.getCStat())) {
                    return new ResultadoEmissao(Status.CONTINGENCIA, chave, retorno.getCStat(), retorno.getXMotivo(),
                            null, envio, false);
                }
                TProtNFe protocolo = retorno.getProtNFe();
                if (protocolo == null) {
                    return new ResultadoEmissao(Status.REJEITADA, chave, retorno.getCStat(), retorno.getXMotivo(), null,
                            envio, false);
                }
                if ("204".equals(protocolo.getInfProt().getCStat())) {
                    // Duplicidade: um envio anterior foi autorizado; o protocolo vem da consulta.
                    return consultar(chave, envio).orElseRejeitada(protocolo);
                }
                return resultado(chave, protocolo, envio, false);
            } catch (NfeException e) {
                if (!falhaDeComunicacao(e)) {
                    throw e;
                }
                // Não sabemos se a SEFAZ autorizou: consultar antes de reenviar evita duplicidade.
                Consulta consulta = consultar(chave, envio);
                if (consulta.encontrada()) {
                    return consulta.resultado();
                }
            }
        }
        return new ResultadoEmissao(Status.CONTINGENCIA, chave, null, "SEFAZ sem resposta após " + MAX_ENVIOS + " envios",
                null, MAX_ENVIOS, false);
    }

    private Consulta consultar(String chave, int envios) throws NfeException {
        TRetConsSitNFe consulta = Nfe.consultaXml(configuracoes.get(), chave, DocumentoEnum.NFE);
        if (consulta.getProtNFe() == null) {
            // 217: a nota não chegou à SEFAZ; reenviar é seguro.
            return new Consulta(null);
        }
        return new Consulta(resultado(chave, consulta.getProtNFe(), envios, true));
    }

    private static ResultadoEmissao resultado(String chave, TProtNFe protocolo, int envios, boolean porConsulta) {
        TProtNFe.InfProt inf = protocolo.getInfProt();
        Status status = "100".equals(inf.getCStat())
                ? Status.AUTORIZADA
                : DENEGACOES.contains(inf.getCStat()) ? Status.DENEGADA : Status.REJEITADA;
        return new ResultadoEmissao(status, chave, inf.getCStat(), inf.getXMotivo(), inf.getNProt(), envios, porConsulta);
    }

    /** Timeout, conexão fechada ou HTTP 5xx: a Java_NFe embrulha a falha de I/O do Axis2. */
    static boolean falhaDeComunicacao(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof IOException) {
                return true;
            }
        }
        return false;
    }

    private record Consulta(ResultadoEmissao resultado) {
        boolean encontrada() {
            return resultado != null;
        }

        ResultadoEmissao orElseRejeitada(TProtNFe protocolo) {
            if (resultado != null) {
                return resultado;
            }
            TProtNFe.InfProt inf = protocolo.getInfProt();
            return new ResultadoEmissao(Status.REJEITADA, inf.getChNFe(), inf.getCStat(), inf.getXMotivo(), null, 1, false);
        }
    }
}
