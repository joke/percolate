package examples.switches;

import io.github.joke.percolate.Mapper;
import java.time.Instant;
import java.time.LocalDateTime;

// tag::mapper[]
@Mapper
public interface ZonedMapper {

    // Crosses families: no directive zone, so the resolved zone depends on -Apercolate.time.zone.
    LocalDateTime toLocalDateTime(Instant timestamp);
}
// end::mapper[]
