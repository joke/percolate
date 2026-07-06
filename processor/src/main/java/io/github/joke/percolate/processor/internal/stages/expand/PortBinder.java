package io.github.joke.percolate.processor.internal.stages.expand;

import io.github.joke.percolate.processor.internal.graph.PortBinding;
import io.github.joke.percolate.processor.internal.graph.Value;
import io.github.joke.percolate.spi.OperationSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Binds every port of {@code spec} to a feeding source, or declines (design D1 of change
 * {@code target-driven-engine}, decomposed out of {@code ExpandStage.Driver} by {@code decompose-engine-stages}): a
 * spec applies only when every one of its ports resolves a source through the injected {@link PortSourceResolver};
 * the first port that cannot be sourced means the whole producer does not apply.
 */
@RequiredArgsConstructor
final class PortBinder {

    private final PortSourceResolver portSourceResolver;

    /** Every port of {@code spec} bound to a feeding source, or empty when any port resolves none. */
    Optional<List<PortBinding>> bind(
            final Value output, final String parentPath, final OperationSpec spec, final @Nullable Value pinnedSource) {
        final var ports = new ArrayList<PortBinding>();
        for (final var port : spec.getPorts()) {
            final var source = portSourceResolver.sourceForPort(output, parentPath, port, pinnedSource);
            if (source == null) {
                return Optional.empty();
            }
            ports.add(new PortBinding(port, source));
        }
        return Optional.of(List.copyOf(ports));
    }
}
