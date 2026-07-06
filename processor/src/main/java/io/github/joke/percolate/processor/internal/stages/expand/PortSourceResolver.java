package io.github.joke.percolate.processor.internal.stages.expand;

import io.github.joke.percolate.processor.internal.graph.AddValue;
import io.github.joke.percolate.processor.internal.graph.Location;
import io.github.joke.percolate.processor.internal.graph.Value;
import io.github.joke.percolate.spi.Port;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Resolves one {@link Port}'s feeding {@link AddValue} by its declared {@link Port.Sourcing} mode (design D1 of
 * change {@code target-driven-engine}, decomposed out of {@code ExpandStage.Driver} by
 * {@code decompose-engine-stages}): {@code SUBTARGET} mints a deeper child-target demand; {@code REUSE} and
 * {@code REUSE_OR_MINT} both bind an in-scope source (directive-pinned first), differing only when none is found —
 * {@code REUSE} declines (returns {@code null}) while {@code REUSE_OR_MINT} mints a fresh intermediate at the output
 * location.
 */
@RequiredArgsConstructor
final class PortSourceResolver {

    private final SourceCandidates sourceCandidates;
    private final OperationLander operationLander;

    /** The feeding {@link AddValue} for {@code port} on {@code output}, or {@code null} when a REUSE port finds none. */
    @Nullable
    AddValue sourceForPort(
            final Value output, final String parentPath, final Port port, final @Nullable Value pinnedSource) {
        if (port.getSourcing() == Port.Sourcing.SUBTARGET) {
            return new AddValue(
                    output.getScope(), Location.child(parentPath, port.getName()), port.getType(), port.getNullness());
        }
        final var reused = sourceCandidates.matchingSource(output.getScope(), port, pinnedSource);
        if (reused != null) {
            return operationLander.reuse(reused);
        }
        // A REUSE port whose input is larger than its output is never minted (you never wrap a value just to
        // unwrap it): with no in-scope source the consuming operation simply does not apply.
        return port.getSourcing() == Port.Sourcing.REUSE
                ? null
                : new AddValue(output.getScope(), output.getLoc(), port.getType(), port.getNullness());
    }
}
