package io.github.joke.percolate.docs.optionals;

import io.github.joke.percolate.Mapper;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

// tag::mapper[]
@Mapper
public interface PreferencesMapper {

    // wrap: a plain (possibly-null) source scalar becomes an Optional target — Optional.ofNullable.
    Optional<String> wrapBio(@Nullable String bio);

    // unwrap into a non-null target: an absent Optional source has nothing to fall back to, so this
    // throws if the source is empty — Optional.orElseThrow().
    String unwrapHandle(Optional<String> handle);

    // unwrap into a nullable target: an absent Optional source collapses to null — Optional.orElse(null).
    @Nullable
    String unwrapNickname(Optional<String> nickname);
}
// end::mapper[]
