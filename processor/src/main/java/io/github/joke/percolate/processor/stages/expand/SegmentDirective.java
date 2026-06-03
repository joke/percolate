package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.spi.Directive;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

/**
 * A synthetic single-segment {@link Directive} the driver hands to a path-resolver strategy during source descent:
 * its {@link #sourcePath()} is exactly the one segment to resolve against the parent candidate. Carries no
 * attributes — descent reads only the segment.
 */
@RequiredArgsConstructor
final class SegmentDirective implements Directive {

    private final String segment;

    @Override
    public List<String> sourcePath() {
        return List.of(segment);
    }

    @Override
    public Optional<String> attribute(final String name) {
        return Optional.empty();
    }
}
