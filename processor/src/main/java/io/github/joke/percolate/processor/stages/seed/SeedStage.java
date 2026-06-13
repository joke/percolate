package io.github.joke.percolate.processor.stages.seed;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.graph.AccessPath;
import io.github.joke.percolate.processor.graph.MapperGraph;
import io.github.joke.percolate.processor.graph.MethodScope;
import io.github.joke.percolate.processor.graph.SourceLocation;
import io.github.joke.percolate.processor.graph.TargetLocation;
import io.github.joke.percolate.processor.graph.TargetPath;
import io.github.joke.percolate.processor.model.GoalSpec;
import io.github.joke.percolate.processor.model.MapperMappings;
import io.github.joke.percolate.processor.model.MethodMappings;
import io.github.joke.percolate.processor.nullability.NullabilityResolver;
import io.github.joke.percolate.processor.stages.Stage;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

/**
 * Seeds the bipartite {@link MapperGraph} for every abstract mapper method with only its roots and goal spec
 * (design D9): one parameter-root {@link io.github.joke.percolate.processor.graph.Value} per method parameter
 * (typed from the declaration; a Horn base case during expansion) and one return-root Value (typed from the
 * return type; the initial demand). It creates no edges, no groups, and no untyped target-leaf nodes — all
 * producer structure is minted during expansion.
 *
 * <p>The method's validated {@code @Map} directives are folded into a per-level {@link GoalSpec} (the declared
 * bindings, the goal half of the planning problem) and stored on the {@link MapperContext} keyed by the method
 * scope. The directive never lives on a Value; it travels with the demand context the work-list carries.
 *
 * <p>{@code ValidateSourceParameters}/{@code ValidateMappingShape} are hard preconditions; seeding drops no
 * directive silently — every directive contributes to the goal spec.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class SeedStage implements Stage {

    private final NullabilityResolver nullabilityResolver;

    @Override
    public void run(final MapperContext ctx) {
        final var mappings = ctx.getMappings();
        if (mappings == null) {
            return;
        }
        ctx.setGraph(apply(ctx, mappings));
    }

    MapperGraph apply(final MapperContext ctx, final MapperMappings mappings) {
        final var graph = new MapperGraph();
        for (final var methodMappings : mappings.getMethods()) {
            seedMethod(ctx, graph, methodMappings);
        }
        return graph;
    }

    private void seedMethod(final MapperContext ctx, final MapperGraph graph, final MethodMappings methodMappings) {
        final var method = methodMappings.getMethod();
        final var scope = new MethodScope(method);

        for (final var param : method.getParameters()) {
            final var loc =
                    new SourceLocation(AccessPath.of(param.getSimpleName().toString()));
            final var nullness = nullabilityResolver.resolve(param.asType(), param);
            graph.valueFor(scope, loc, param.asType(), nullness);
        }

        final var returnType = method.getReturnType();
        final var returnNullness = nullabilityResolver.resolve(returnType, method);
        graph.valueFor(scope, new TargetLocation(TargetPath.of("")), returnType, returnNullness);

        ctx.getGoalSpecs().put(scope, GoalSpec.from(methodMappings.getDirectives()));
    }
}
