package io.github.vbaggio.sefaz.examples.javanfe;

import br.com.swconsultoria.nfe.schemas.TEnviNFe;
import br.com.swconsultoria.nfe.schemas.TNFe;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Lote com uma NF-e mínima. A SEFAZ fake não valida o leiaute (ADR-001), então só os campos que
 * formam a chave e os que a Java_NFe usa para assinar são preenchidos.
 */
public final class NotaFicticia {

    /** CNPJ de exemplo de documentação, não pertence a uma empresa real conhecida. */
    public static final String CNPJ = "11222333000181";
    public static final String RAZAO_SOCIAL = "EMPRESA FICTICIA LTDA";
    private static final String CUF_SP = "35";

    private NotaFicticia() {
    }

    public static TEnviNFe numero(int numero) {
        OffsetDateTime emissao = OffsetDateTime.now(ZoneId.of("America/Sao_Paulo")).withNano(0);
        String cNF = String.format("%08d", 10_000_000 + numero);
        String semDv = CUF_SP + emissao.format(DateTimeFormatter.ofPattern("yyMM")) + CNPJ + "55" + "001"
                + String.format("%09d", numero) + "1" + cNF;
        String dv = digitoVerificador(semDv);

        TNFe.InfNFe.Ide ide = new TNFe.InfNFe.Ide();
        ide.setCUF(CUF_SP);
        ide.setCNF(cNF);
        ide.setNatOp("VENDA");
        ide.setMod("55");
        ide.setSerie("1");
        ide.setNNF(Integer.toString(numero));
        ide.setDhEmi(emissao.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        ide.setTpNF("1");
        ide.setIdDest("1");
        ide.setCMunFG("3550308");
        ide.setTpImp("1");
        ide.setTpEmis("1");
        ide.setCDV(dv);
        ide.setTpAmb("2");
        ide.setFinNFe("1");
        ide.setIndFinal("1");
        ide.setIndPres("1");
        ide.setProcEmi("0");
        ide.setVerProc("java-nfe-example");

        TNFe.InfNFe.Emit emit = new TNFe.InfNFe.Emit();
        emit.setCNPJ(CNPJ);
        emit.setXNome(RAZAO_SOCIAL);
        emit.setIE("ISENTO");
        emit.setCRT("1");

        TNFe.InfNFe infNFe = new TNFe.InfNFe();
        infNFe.setId("NFe" + semDv + dv);
        infNFe.setVersao("4.00");
        infNFe.setIde(ide);
        infNFe.setEmit(emit);

        TNFe nfe = new TNFe();
        nfe.setInfNFe(infNFe);

        TEnviNFe lote = new TEnviNFe();
        lote.setVersao("4.00");
        lote.setIdLote("1");
        lote.setIndSinc("1");
        lote.getNFe().add(nfe);
        return lote;
    }

    public static String chave(TEnviNFe lote) {
        return lote.getNFe().get(0).getInfNFe().getId().substring(3);
    }

    /** Módulo 11 com pesos 2 a 9, da direita para a esquerda. */
    static String digitoVerificador(String chaveSemDv) {
        int soma = 0;
        int peso = 2;
        for (int i = chaveSemDv.length() - 1; i >= 0; i--) {
            soma += (chaveSemDv.charAt(i) - '0') * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        return Integer.toString(resto < 2 ? 0 : 11 - resto);
    }
}
