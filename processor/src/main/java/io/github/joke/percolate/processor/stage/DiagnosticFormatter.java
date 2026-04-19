package io.github.joke.percolate.processor.stage;

import io.github.joke.percolate.processor.Diagnostic;
import io.github.joke.percolate.processor.ErrorMessages;
import io.github.joke.percolate.processor.graph.TargetSlotNode;
import io.github.joke.percolate.processor.spi.ValueExpansionStrategy;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;
import lombok.NoArgsConstructor;

/**
 * Formats diagnostics emitted at the {@code BuildValueGraphStage} boundary.
 *
 * <p>This replaces the user-facing role of the now-removed {@code ValidateResolutionStage}: after
 * demand-driven expansion terminates, any {@link TargetSlotNode} that still lacks a path back to a
 * {@code SourceParamNode}, or any {@code MappingAssignment} whose source-path segment cannot be
 * resolved, produces a {@link Diagnostic} via this helper.
 */
@NoArgsConstructor(onConstructor_ = @Inject)
public final class DiagnosticFormatter {

    /**
     * Format the "no path found" diagnostic for an unreached target slot after expansion
     * terminates.
     *
     * @param location the element the diagnostic should attach to (typically the mapping method)
     * @param slot the target slot that never received an incoming edge chain
     * @param searched the types the expander visited while trying to reach the slot
     * @param tried the strategies consulted in priority order
     */
    public Diagnostic noPathFound(
            final Element location,
            final TargetSlotNode slot,
            final Set<TypeMirror> searched,
            final List<ValueExpansionStrategy> tried) {
        final var sorted = new TreeSet<String>();
        for (final var t : searched) {
            sorted.add(t.toString());
        }
        final var strategyNames = tried.stream()
                .map(s -> s.getClass().getSimpleName())
                .collect(Collectors.joining(", "));
        final var message = new StringBuilder();
        message.append("No expansion path found for target slot '")
                .append(slot.getName())
                .append("' of type ")
                .append(slot.getType())
                .append(".\n");
        message.append("  Types visited: ").append(sorted).append('\n');
        message.append("  Strategies consulted: [").append(strategyNames).append(']');
        return new Diagnostic(location, message.toString(), Kind.ERROR);
    }

    /**
     * Format the "missing property" diagnostic when a {@code sourcePath} segment does not resolve.
     *
     * @param location the element the diagnostic should attach to
     * @param segment the unresolved path segment
     * @param index zero-based position of the segment in the full source path
     * @param searchedType the source type on which the segment was not found
     * @param available the property names actually present on {@code searchedType}
     */
    public Diagnostic missingProperty(
            final Element location,
            final String segment,
            final int index,
            final TypeMirror searchedType,
            final List<String> available) {
        final var message = new StringBuilder();
        message.append("Unknown source property segment '")
                .append(segment)
                .append("' at index ")
                .append(index)
                .append(" on type ")
                .append(searchedType)
                .append(".\n");
        final var sorted = new TreeSet<>(available);
        message.append("  Available properties: ").append(sorted);
        return new Diagnostic(location, message.toString(), Kind.ERROR);
    }
}
