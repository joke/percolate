package io.github.joke.percolate.processor.internal.stages.expand;

import io.github.joke.percolate.processor.internal.graph.AddValue;
import io.github.joke.percolate.processor.internal.graph.Location;
import io.github.joke.percolate.processor.internal.graph.Refusal;
import io.github.joke.percolate.processor.internal.graph.Value;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.Subject;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

// Resolves one Port's feeding AddValue by its declared axes (design D5 of change decouple-engine-from-strategy-
// semantics, decomposed out of ExpandStage.Driver by decompose-engine-stages): a sub-target port mints a deeper
// child-target demand; otherwise the port is sourced by its Port.Selector (BY_TYPE matches an in-scope source,
// directive-pinned first; BY_NAME resolves the scope's named input) and a miss is handled by its Port.OnMiss
// rule — DECLINE returns null, MINT mints a fresh intermediate at the output location, and REQUIRE records a
// refusal on output and returns null. The engine stays diagnostics-free for a DECLINE/MINT miss; a REQUIRE miss
// is the one case the engine itself reports, in port vocabulary, because an unsourceable required port is the
// engine failing its own declared contract.
@RequiredArgsConstructor
final class PortSourceResolver {

    private final SourceCandidates sourceCandidates;
    private final OperationLander operationLander;

    // The feeding AddValue for port on output, or null when the port finds no source. subject positions any
    // REQUIRE-miss refusal — the spec's call target when present, else Subjects.none().
    @Nullable
    AddValue sourceForPort(
            final Value output,
            final String parentPath,
            final Port port,
            final @Nullable Value pinnedSource,
            final Subject subject) {
        if (port.isSubTarget()) {
            return new AddValue(
                    output.getScope(), Location.child(parentPath, port.getName()), port.getType(), port.getNullness());
        }
        final var bound = boundSource(output, port, pinnedSource);
        return bound != null ? operationLander.reuse(bound) : onMiss(output, port, subject);
    }

    @Nullable
    Value boundSource(final Value output, final Port port, final @Nullable Value pinnedSource) {
        return port.getSelector() == Port.Selector.BY_NAME
                ? sourceCandidates.byNameSource(output.getScope(), port)
                : sourceCandidates.matchingSource(output.getScope(), port, pinnedSource);
    }

    @Nullable
    AddValue onMiss(final Value output, final Port port, final Subject subject) {
        if (port.getOnMiss() == Port.OnMiss.MINT) {
            return new AddValue(output.getScope(), output.getLoc(), port.getType(), port.getNullness());
        }
        recordRequireRefusal(output, port, subject);
        return null;
    }

    void recordRequireRefusal(final Value output, final Port port, final Subject subject) {
        if (port.getOnMiss() == Port.OnMiss.REQUIRE) {
            output.addInadmissible(new Refusal(subject, requireMissMessage(output, port)));
        }
    }

    // Names the port, the binding name, and — for a resolvable-but-mismatched name — both types.
    String requireMissMessage(final Value output, final Port port) {
        return sourceCandidates
                .byNameDeclaredType(output.getScope(), port)
                .map(declaredType -> "port '" + port.getName() + "' names scope input '" + port.getBindingName()
                        + "' of type " + declaredType + ", not assignable to declared type " + port.getType())
                .orElseGet(() -> "port '" + port.getName() + "' names scope input '" + port.getBindingName()
                        + "', which no enclosing scope publishes");
    }
}
