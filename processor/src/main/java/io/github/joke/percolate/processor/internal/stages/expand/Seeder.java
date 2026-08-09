package io.github.joke.percolate.processor.internal.stages.expand;

import io.github.joke.percolate.processor.internal.graph.AccessPath;
import io.github.joke.percolate.processor.internal.graph.AddValue;
import io.github.joke.percolate.processor.internal.graph.InputDecl;
import io.github.joke.percolate.processor.internal.graph.MapperGraph;
import io.github.joke.percolate.processor.internal.graph.MethodScope;
import io.github.joke.percolate.processor.internal.graph.Scope;
import io.github.joke.percolate.processor.internal.graph.SourceLocation;
import io.github.joke.percolate.processor.internal.graph.TargetLocation;
import io.github.joke.percolate.processor.internal.graph.TargetPath;
import io.github.joke.percolate.processor.internal.graph.Value;
import io.github.joke.percolate.processor.internal.graph.Visibility;
import io.github.joke.percolate.processor.model.GoalSpec;
import io.github.joke.percolate.processor.model.ScopeInputOverride;
import io.github.joke.percolate.processor.nullability.NullabilityResolver;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import lombok.RequiredArgsConstructor;

import static java.util.stream.Collectors.toUnmodifiableList;

// Mints one method's return-root Value (decomposed out of ExpandStage.Driver.seedReturnRoot by change
// decompose-engine-stages): the only seed of an expansion run, landed through the Applier and marked as the
// method's return root — the authority a method may not satisfy by self-call, and the single root
// extraction/diagnostics/codegen key on. Builds the method's MethodScope with one resolved InputDecl per
// parameter (design D5/D7 of change decouple-engine-from-strategy-semantics) — the one place a method's
// parameters are read and resolved, since MethodScope itself is plain data. A parameter's name/visibility
// default to its own simple name/Visibility.LOCAL unless a io.github.joke.percolate.spi.DirectiveReader
// published a ScopeInputOverride for it (e.g. @Ambient) — the engine reads no annotation itself.
@RequiredArgsConstructor
final class Seeder {

    private final MapperGraph graph;
    private final Applier applier;
    private final NullabilityResolver resolver;
    private final Map<Scope, GoalSpec> goalSpecs;

    // Mints and marks the return-root Value for method.
    Value seed(final ExecutableElement method) {
        final var scope = new MethodScope(method, declarationsFor(method));
        final var returnType = method.getReturnType();
        final var nullness = resolver.resolve(returnType, method);
        final var root =
                applier.apply(graph, new AddValue(scope, new TargetLocation(TargetPath.of("")), returnType, nullness));
        graph.markReturnRoot(root);
        return root;
    }

    // One resolved InputDecl per parameter, named and visibility-marked per any published override.
    List<InputDecl> declarationsFor(final ExecutableElement method) {
        final var overrideByParam = scopeInputOverridesFor(method);
        return method.getParameters().stream()
                .map(param -> new InputDecl(
                        new SourceLocation(AccessPath.of(param.getSimpleName().toString())),
                        param.asType(),
                        resolver.resolve(param.asType(), param),
                        nameOf(param, overrideByParam),
                        visibilityOf(param, overrideByParam)))
                .collect(toUnmodifiableList());
    }

    Map<VariableElement, ScopeInputOverride> scopeInputOverridesFor(final ExecutableElement method) {
        final var goalSpec = goalSpecs.get(new MethodScope(method));
        if (goalSpec == null) {
            return Map.of();
        }
        return goalSpec.getScopeInputOverrides().stream()
                .collect(Collectors.toMap(
                        ScopeInputOverride::getParameter, Function.identity(), (first, second) -> first));
    }

    String nameOf(final VariableElement param, final Map<VariableElement, ScopeInputOverride> overrideByParam) {
        final var override = overrideByParam.get(param);
        return override == null ? param.getSimpleName().toString() : override.getName();
    }

    Visibility visibilityOf(
            final VariableElement param, final Map<VariableElement, ScopeInputOverride> overrideByParam) {
        final var override = overrideByParam.get(param);
        if (override == null) {
            return Visibility.LOCAL;
        }
        return override.getVisibility() == io.github.joke.percolate.spi.Visibility.INHERITED
                ? Visibility.INHERITED
                : Visibility.LOCAL;
    }
}
