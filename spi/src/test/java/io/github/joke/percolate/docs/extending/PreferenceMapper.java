package io.github.joke.percolate.docs.extending;

import io.github.joke.percolate.Mapper;
import java.util.Optional;
import reactor.core.publisher.Mono;

// tag::mapper[]
// reactor is on the annotationProcessor classpath alongside the percolate starter, and its
// JustOrEmpty strategy — a genuine third-party extension, not special-cased by the engine — is what
// produces this method.
@Mapper
public interface PreferenceMapper {

    Mono<String> wrap(Optional<String> value);
}
// end::mapper[]
