package io.github.joke.percolate.processor.internal.stages.expand;

import io.github.joke.percolate.processor.internal.graph.AddValue;
import io.github.joke.percolate.processor.internal.graph.MapperGraph;
import io.github.joke.percolate.processor.internal.graph.MethodScope;
import io.github.joke.percolate.processor.internal.graph.TargetLocation;
import io.github.joke.percolate.processor.internal.graph.TargetPath;
import io.github.joke.percolate.processor.internal.graph.Value;
import io.github.joke.percolate.processor.nullability.NullabilityResolver;
import javax.lang.model.element.ExecutableElement;
import lombok.RequiredArgsConstructor;

/**
 * Mints one method's return-root {@code Value} (decomposed out of {@code ExpandStage.Driver.seedReturnRoot} by
 * change {@code decompose-engine-stages}): the only seed of an expansion run, landed through the {@link Applier} and
 * marked as the method's return root — the authority a method may not satisfy by self-call, and the single root
 * extraction/diagnostics/codegen key on.
 */
@RequiredArgsConstructor
final class Seeder {

    private final MapperGraph graph;
    private final Applier applier;
    private final NullabilityResolver resolver;

    /** Mints and marks the return-root {@code Value} for {@code method}. */
    Value seed(final ExecutableElement method) {
        final var scope = new MethodScope(method);
        final var returnType = method.getReturnType();
        final var nullness = resolver.resolve(returnType, method);
        final var root =
                applier.apply(graph, new AddValue(scope, new TargetLocation(TargetPath.of("")), returnType, nullness));
        graph.markReturnRoot(root);
        return root;
    }
}
