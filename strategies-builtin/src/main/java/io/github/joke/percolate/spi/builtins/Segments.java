package io.github.joke.percolate.spi.builtins;

import io.github.joke.percolate.spi.Directive;
import io.github.joke.percolate.spi.Frontier;
import java.util.Optional;
import lombok.experimental.UtilityClass;

/**
 * Shared helper for path-resolver strategies: extracts the single source-path segment the driver feeds for source
 * descent. A path resolver fires only when the frontier carries a {@link io.github.joke.percolate.spi.Directive}
 * whose {@code sourcePath()} is exactly one segment — the next member to resolve against a candidate (parent) type.
 */
@UtilityClass
class Segments {

    private static final int SINGLE_SEGMENT = 1;

    Optional<String> single(final Frontier frontier) {
        return frontier.directive()
                .map(Directive::sourcePath)
                .filter(path -> path.size() == SINGLE_SEGMENT)
                .map(path -> path.get(0));
    }
}
