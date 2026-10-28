package io.github.vbaggio.sefaz.mock.store;

import io.github.vbaggio.sefaz.mock.nfe.Numeracao;
import java.util.Collection;
import java.util.Optional;

/** Estado das notas, recibos e numeradores de um container. */
public interface NotaStore {

    Optional<NotaRecord> find(String chave);

    /** Nota na base (autorizada ou denegada) com a mesma numeração. */
    Optional<NotaRecord> findByNumeracao(Numeracao numeracao);

    void save(NotaRecord nota);

    Collection<NotaRecord> all();

    void saveRecibo(Recibo recibo);

    Optional<Recibo> findRecibo(String nRec);

    /** Próximo número de protocolo, 15 dígitos, sequencial no container. */
    String nextProtocolo(String cUF, int ano);

    /** Próximo número de recibo, 15 dígitos, sequencial no container. */
    String nextRecibo(String cUF);

    /** Apaga notas, recibos e reinicia os numeradores. */
    void clear();
}
