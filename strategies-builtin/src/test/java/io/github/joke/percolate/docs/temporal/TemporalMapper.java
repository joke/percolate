package io.github.joke.percolate.docs.temporal;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

// tag::mapper[]
@Mapper
public interface TemporalMapper {

    // Same-family spokes compose through the Instant hub automatically — no @Map needed.
    Instant toInstant(java.util.Date createdAt);

    // A cross-spoke pair (both absolute-family) composes through Instant too: OffsetDateTime -> Instant -> Date.
    java.util.Date toDate(OffsetDateTime receivedAt);

    // Crossing families uses the zone bridge. With neither a directive nor a
    // -Apercolate.time.zone option, the generated code defers to the consumer's runtime default zone.
    LocalDateTime toLocalDateTime(Instant timestamp);

    // A directive-declared zone freezes it into the generated code instead.
    @Map(target = "", source = "timestamp", zone = "Europe/Berlin")
    LocalDateTime toBerlinTime(Instant timestamp);

    // @Map(format = ...) parses/renders Strings against java.time types via a shared DateTimeFormatter.
    @Map(target = "", source = "text", format = "yyyy-MM-dd")
    LocalDate parseDate(String text);

    @Map(target = "", source = "date", format = "yyyy-MM-dd")
    String formatDate(LocalDate date);
}
// end::mapper[]
