package io.github.joke.percolate.processor.model;

import io.github.joke.percolate.spi.Constraint;
import io.github.joke.percolate.spi.Directive;
import io.github.joke.percolate.spi.DirectiveInput;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.NoArgsConstructor;

import static java.lang.String.join;

// Derives a GoalSpec from one method's sink-recorded facts. Split from GoalSpec by change tighten-testability-
// conventions (design D2): the derivation rules — every path segment declaring a child at its parent level,
// first-bind-wins on a duplicate target, a directive existing for an input-only path no bind ever touched — are
// the decisions worth testing, and as statics on the value type they could not be intercepted. GoalSpec is left
// as the queryable goal half of the planning problem.
@NoArgsConstructor(onConstructor_ = @Inject)
public class GoalSpecFactory {

    // Derives the per-level goal spec from one method's sink-recorded facts (design D7): binds contribute declared
    // children and a target's source path; inputsByTarget/constraintsByTarget are keyed by the same dotted target
    // path a Bind would use, and may name a path no binding ever touched; scopeInputOverrides carries every
    // published parameter override, consumed by whoever seeds the method's scope.
    public GoalSpec from(
            final List<Bind> binds,
            final Map<String, List<DirectiveInput>> inputsByTarget,
            final Map<String, List<Constraint>> constraintsByTarget,
            final List<ScopeInputOverride> scopeInputOverrides) {
        final var levels = childLevels(binds);
        final var sourceByTarget = sourcePathByTarget(binds);
        return new GoalSpec(
                Map.copyOf(levels),
                Map.copyOf(directivesByTarget(sourceByTarget, inputsByTarget)),
                Map.copyOf(constraintsByTarget),
                List.copyOf(scopeInputOverrides));
    }

    // Parent dotted path -> declared child names at that level, derived from every bind's target path segments.
    Map<String, Set<String>> childLevels(final List<Bind> binds) {
        final var levels = new LinkedHashMap<String, Set<String>>();
        for (final var bind : binds) {
            final var segments = bind.getTargetPath();
            for (var i = 0; i < segments.size(); i++) {
                final var parent = join(".", segments.subList(0, i));
                levels.computeIfAbsent(parent, key -> new LinkedHashSet<>()).add(segments.get(i));
            }
        }
        return levels;
    }

    // Exact dotted target path -> the first bind's source path declared there (first bind wins on a duplicate).
    Map<String, List<String>> sourcePathByTarget(final List<Bind> binds) {
        final var sourceByTarget = new LinkedHashMap<String, List<String>>();
        for (final var bind : binds) {
            sourceByTarget.putIfAbsent(join(".", bind.getTargetPath()), bind.getSourcePath());
        }
        return sourceByTarget;
    }

    // A Directive per path either bound or input-only, merging both sets of dotted target paths.
    Map<String, Directive> directivesByTarget(
            final Map<String, List<String>> sourceByTarget, final Map<String, List<DirectiveInput>> inputsByTarget) {
        final var directives = new LinkedHashMap<String, Directive>();
        final var paths = new LinkedHashSet<String>(sourceByTarget.keySet());
        paths.addAll(inputsByTarget.keySet());
        for (final var path : paths) {
            directives.put(
                    path,
                    new GenericDirective(
                            sourceByTarget.getOrDefault(path, List.of()),
                            inputsByTarget.getOrDefault(path, List.of())));
        }
        return directives;
    }
}
