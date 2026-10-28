package io.github.vbaggio.sefaz.testcontainers;

import java.util.Objects;

/**
 * Critério de uma regra de cenário. Campos não informados não restringem; série e número são
 * comparados sem zeros à esquerda e o CNPJ, sem pontuação.
 *
 * <pre>{@code
 * Match.numero("123")
 * Match.serie("1").numero("123")
 * Match.operation(SefazService.AUTORIZACAO).cnpj("11222333000181")
 * }</pre>
 *
 * <p>As fábricas estáticas devolvem {@link Criteria}, que encadeia os demais campos.
 */
public sealed interface Match permits Match.Criteria {

    SefazService operation();

    String chave();

    String cnpj();

    String serie();

    String numero();

    /** Qualquer requisição. */
    static Criteria any() {
        return Criteria.ANY;
    }

    static Criteria operation(SefazService operation) {
        return Criteria.ANY.operation(operation);
    }

    static Criteria chave(String chave) {
        return Criteria.ANY.chave(chave);
    }

    static Criteria cnpj(String cnpj) {
        return Criteria.ANY.cnpj(cnpj);
    }

    static Criteria serie(String serie) {
        return Criteria.ANY.serie(serie);
    }

    static Criteria numero(String numero) {
        return Criteria.ANY.numero(numero);
    }

    static Criteria numero(long numero) {
        return Criteria.ANY.numero(Long.toString(numero));
    }

    /** Implementação imutável de {@link Match}; cada método devolve uma cópia com o campo a mais. */
    final class Criteria implements Match {

        private static final Criteria ANY = new Criteria(null, null, null, null, null);

        private final SefazService operation;
        private final String chave;
        private final String cnpj;
        private final String serie;
        private final String numero;

        private Criteria(SefazService operation, String chave, String cnpj, String serie, String numero) {
            this.operation = operation;
            this.chave = chave;
            this.cnpj = cnpj;
            this.serie = serie;
            this.numero = numero;
        }

        public Criteria operation(SefazService value) {
            return new Criteria(Objects.requireNonNull(value), chave, cnpj, serie, numero);
        }

        public Criteria chave(String value) {
            return new Criteria(operation, Objects.requireNonNull(value), cnpj, serie, numero);
        }

        public Criteria cnpj(String value) {
            return new Criteria(operation, chave, Objects.requireNonNull(value), serie, numero);
        }

        public Criteria serie(String value) {
            return new Criteria(operation, chave, cnpj, Objects.requireNonNull(value), numero);
        }

        public Criteria numero(String value) {
            return new Criteria(operation, chave, cnpj, serie, Objects.requireNonNull(value));
        }

        public Criteria numero(long value) {
            return numero(Long.toString(value));
        }

        @Override
        public SefazService operation() {
            return operation;
        }

        @Override
        public String chave() {
            return chave;
        }

        @Override
        public String cnpj() {
            return cnpj;
        }

        @Override
        public String serie() {
            return serie;
        }

        @Override
        public String numero() {
            return numero;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Criteria m
                    && operation == m.operation
                    && Objects.equals(chave, m.chave)
                    && Objects.equals(cnpj, m.cnpj)
                    && Objects.equals(serie, m.serie)
                    && Objects.equals(numero, m.numero);
        }

        @Override
        public int hashCode() {
            return Objects.hash(operation, chave, cnpj, serie, numero);
        }

        @Override
        public String toString() {
            return "Match{operation=" + operation + ", chave=" + chave + ", cnpj=" + cnpj + ", serie=" + serie
                    + ", numero=" + numero + "}";
        }
    }
}
