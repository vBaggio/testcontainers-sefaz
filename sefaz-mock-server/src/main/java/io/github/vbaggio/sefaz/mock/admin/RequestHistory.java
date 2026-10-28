package io.github.vbaggio.sefaz.mock.admin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Histórico em memória das requisições SOAP, limitado às mais recentes. */
public final class RequestHistory {

    private final Deque<RecordedRequest> entries = new ArrayDeque<>();
    private final AtomicLong ids = new AtomicLong();
    private final int limit;

    public RequestHistory(int limit) {
        this.limit = limit;
    }

    public long nextId() {
        return ids.incrementAndGet();
    }

    public synchronized void add(RecordedRequest request) {
        entries.addLast(request);
        while (entries.size() > limit) {
            entries.removeFirst();
        }
    }

    public synchronized List<RecordedRequest> list(String operation) {
        List<RecordedRequest> result = new ArrayList<>(entries.size());
        for (RecordedRequest r : entries) {
            if (operation == null || operation.equalsIgnoreCase(r.operation())) {
                result.add(r);
            }
        }
        return result;
    }

    public synchronized void clear() {
        entries.clear();
    }
}
