package io.github.joke.percolate.processor;

import jakarta.inject.Inject;
import javax.lang.model.element.TypeElement;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
@EqualsAndHashCode
final class Pipeline {

    private final DiscoverAbstractMethods discoverAbstractMethods;
    private final DiscoverMappings discoverMappings;
    private final ValidateNoDuplicateTargets validateNoDuplicateTargets;
    private final ValidateSourceParameters validateSourceParameters;
    private final SeedGraph seedGraph;
    private final DumpGraph dumpGraph;

    void process(final TypeElement element) {
        final var shape = discoverAbstractMethods.apply(element);
        final var mappings = discoverMappings.apply(shape);
        validateNoDuplicateTargets.validate(mappings);
        validateSourceParameters.validate(mappings);
        final var graph = seedGraph.apply(mappings);
        dumpGraph.apply(graph, element);
    }
}
