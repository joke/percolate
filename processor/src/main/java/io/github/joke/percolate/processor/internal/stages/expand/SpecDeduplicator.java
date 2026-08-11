package io.github.joke.percolate.processor.internal.stages.expand;

import io.github.joke.percolate.spi.OperationSpec;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.NoArgsConstructor;

import static java.util.stream.Collectors.joining;

// Drops duplicate OperationSpecs from an over-emitted offer set, keeping the first of each structural
// signature. Two strategies independently offering the same operation is normal under over-emission (design
// "the engine never chooses"), so both TargetProducer and SourcePathDescender deduplicate — which is why this
// is its own collaborator rather than a helper on either: change tighten-testability-conventions made it an
// instance method, and a shared instance method needs an owner both callers can hold.
@NoArgsConstructor
final class SpecDeduplicator {

    // specs with duplicate structural signatures dropped, preserving first-seen order.
    List<OperationSpec> dedup(final List<OperationSpec> specs) {
        final var seen = new LinkedHashSet<String>();
        final var unique = new ArrayList<OperationSpec>();
        for (final var spec : specs) {
            if (seen.add(signature(spec))) {
                unique.add(spec);
            }
        }
        return unique;
    }

    // The structural signature (label, output type, port shapes) two specs share iff they are duplicates.
    String signature(final OperationSpec spec) {
        final var ports = spec.getPorts().stream()
                .map(port -> port.getName() + ':' + port.getType() + ':' + port.getNullness())
                .collect(joining(","));
        return spec.getLabel() + '|' + spec.getOutputType() + '|' + ports;
    }
}
