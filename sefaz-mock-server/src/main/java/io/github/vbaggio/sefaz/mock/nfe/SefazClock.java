package io.github.vbaggio.sefaz.mock.nfe;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** Relógio da SEFAZ fake: horário de Brasília, formato {@code AAAA-MM-DDThh:mm:ssTZD}. */
public final class SefazClock {

    public static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final Clock clock;

    public SefazClock(Clock clock) {
        this.clock = clock.withZone(BRASILIA);
    }

    public static SefazClock system() {
        return new SefazClock(Clock.system(BRASILIA));
    }

    public OffsetDateTime now() {
        return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    public static String format(OffsetDateTime value) {
        return value == null ? "" : FORMATO.format(value);
    }
}
