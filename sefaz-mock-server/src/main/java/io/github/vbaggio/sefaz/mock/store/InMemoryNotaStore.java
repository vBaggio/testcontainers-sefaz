package io.github.vbaggio.sefaz.mock.store;

import io.github.vbaggio.sefaz.mock.nfe.Numeracao;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** {@link NotaStore} em memória, thread-safe. O estado some quando o container para. */
public final class InMemoryNotaStore implements NotaStore {

    private final ConcurrentHashMap<String, NotaRecord> notas = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Numeracao, String> chavesPorNumeracao = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Recibo> recibos = new ConcurrentHashMap<>();
    private final AtomicLong protocolos = new AtomicLong();
    private final AtomicLong numerosRecibo = new AtomicLong();

    @Override
    public Optional<NotaRecord> find(String chave) {
        return Optional.ofNullable(notas.get(chave));
    }

    @Override
    public Optional<NotaRecord> findByNumeracao(Numeracao numeracao) {
        return Optional.ofNullable(chavesPorNumeracao.get(numeracao)).map(notas::get);
    }

    @Override
    public void save(NotaRecord nota) {
        String chave = nota.chave().valor();
        notas.put(chave, nota);
        if (nota.naBase()) {
            chavesPorNumeracao.putIfAbsent(nota.chave().numeracao(), chave);
        }
    }

    @Override
    public Collection<NotaRecord> all() {
        List<NotaRecord> result = new ArrayList<>(notas.values());
        result.sort((a, b) -> a.dhRecbto().compareTo(b.dhRecbto()));
        return result;
    }

    @Override
    public void saveRecibo(Recibo recibo) {
        recibos.put(recibo.nRec(), recibo);
    }

    @Override
    public Optional<Recibo> findRecibo(String nRec) {
        return Optional.ofNullable(nRec).map(recibos::get);
    }

    @Override
    public String nextProtocolo(String cUF, int ano) {
        // 1 = autorizador da UF, depois cUF, ano com 2 dígitos e sequencial de 10 dígitos.
        return String.format("1%s%02d%010d", cUF, ano % 100, protocolos.incrementAndGet());
    }

    @Override
    public String nextRecibo(String cUF) {
        return String.format("%s1%012d", cUF, numerosRecibo.incrementAndGet());
    }

    @Override
    public void clear() {
        notas.clear();
        chavesPorNumeracao.clear();
        recibos.clear();
        protocolos.set(0);
        numerosRecibo.set(0);
    }
}
