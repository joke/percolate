package io.github.joke.percolate.processor.model;

import io.github.joke.percolate.spi.Constraint;
import io.github.joke.percolate.spi.Directive;
import io.github.joke.percolate.spi.DirectiveInput;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Value;

/**
 * The declared-bindings goal spec for one mapper method — the goal half of the planning problem (design D6/D9),
 * assembled generically from whatever a {@link io.github.joke.percolate.spi.DirectiveReader} declared through a
 * {@code DirectiveSink} (design D7 of change {@code decouple-engine-from-strategy-semantics}): every {@link Bind}
 * contributes its next path segment as a declared child at its parent level, and the full target path maps to a
 * {@link Directive} assembled from that binding's source path (if any) plus every input attached at the same path —
 * present even when no {@code bind} ever touched the path (e.g. a {@code @MapEnum} table, attached only at the
 * empty root path).
 *
 * <p>{@link #declaredChildren(String)} gates assembly at a target level (a constructor is a candidate iff its
 * parameter-name set equals the declared-children set); {@link #bindingFor(String)} is the {@link Directive} a
 * target Value's demand carries; {@link #constraintsFor(String)} is the demand-scoped admissibility constraints a
 * reader attached there (design D8). A directive never lives on a {@code Value}; the goal spec travels with the
 * demand context.
 */
@Value
public class GoalSpec {

    /** Parent dotted path -> declared child names at that level (insertion-ordered for determinism). */
    Map<String, Set<String>> childrenByLevel;

    /** Exact dotted target path -> the {@link Directive} assembled there. */
    Map<String, Directive> directiveByTarget;

    /** Exact dotted target path -> the demand-scoped {@link Constraint}s a reader attached there. */
    Map<String, List<Constraint>> constraintsByTarget;

    /** Every {@code scopeInput} override a reader published for this method (design D5/D7), in declaration order. */
    List<ScopeInputOverride> scopeInputOverrides;

    /** The declared child names at {@code parentPath} (empty when the level declares nothing). */
    public Set<String> declaredChildren(final String parentPath) {
        return childrenByLevel.getOrDefault(parentPath, Set.of());
    }

    /** The {@link Directive} assembled at the exact {@code targetPath}, or empty when the path is purely structural. */
    public Optional<Directive> bindingFor(final String targetPath) {
        return Optional.ofNullable(directiveByTarget.get(targetPath));
    }

    /** The demand-scoped constraints attached at the exact {@code targetPath} (empty when none were attached). */
    public List<Constraint> constraintsFor(final String targetPath) {
        return constraintsByTarget.getOrDefault(targetPath, List.of());
    }

    /** The empty goal spec: no bindings, no inputs, no constraints, no scope-input overrides. */
    public static GoalSpec empty() {
        return from(List.of(), Map.of(), Map.of(), List.of());
    }

    /**
     * Derives the per-level goal spec from one method's sink-recorded facts (design D7): {@code binds} contribute
     * declared children and a target's source path; {@code inputsByTarget}/{@code constraintsByTarget} are keyed by
     * the same dotted target path a {@link Bind} would use, and may name a path no binding ever touched;
     * {@code scopeInputOverrides} carries every published parameter override, consumed by whoever seeds the
     * method's scope.
     */
    public static GoalSpec from(
            final List<Bind> binds,
            final Map<String, List<DirectiveInput>> inputsByTarget,
            final Map<String, List<Constraint>> constraintsByTarget,
            final List<ScopeInputOverride> scopeInputOverrides) {
        final Map<String, Set<String>> levels = childLevels(binds);
        final Map<String, List<String>> sourceByTarget = sourcePathByTarget(binds);
        return new GoalSpec(
                Map.copyOf(levels),
                Map.copyOf(directivesByTarget(sourceByTarget, inputsByTarget)),
                Map.copyOf(constraintsByTarget),
                List.copyOf(scopeInputOverrides));
    }

    /** Parent dotted path -> declared child names at that level, derived from every bind's target path segments. */
    @SuppressWarnings("PMD.UseConcurrentHashMap") // single-threaded per-method derivation; insertion order matters
    static Map<String, Set<String>> childLevels(final List<Bind> binds) {
        final Map<String, Set<String>> levels = new LinkedHashMap<>();
        for (final var bind : binds) {
            final var segments = bind.getTargetPath();
            for (var i = 0; i < segments.size(); i++) {
                final var parent = String.join(".", segments.subList(0, i));
                levels.computeIfAbsent(parent, key -> new LinkedHashSet<>()).add(segments.get(i));
            }
        }
        return levels;
    }

    /** Exact dotted target path -> the first bind's source path declared there (first bind wins on a duplicate). */
    @SuppressWarnings("PMD.UseConcurrentHashMap") // single-threaded per-method derivation; insertion order matters
    static Map<String, List<String>> sourcePathByTarget(final List<Bind> binds) {
        final Map<String, List<String>> sourceByTarget = new LinkedHashMap<>();
        for (final var bind : binds) {
            sourceByTarget.putIfAbsent(String.join(".", bind.getTargetPath()), bind.getSourcePath());
        }
        return sourceByTarget;
    }

    /** A {@link Directive} per path either bound or input-only, merging both sets of dotted target paths. */
    @SuppressWarnings("PMD.UseConcurrentHashMap") // single-threaded per-method derivation; insertion order matters
    static Map<String, Directive> directivesByTarget(
            final Map<String, List<String>> sourceByTarget, final Map<String, List<DirectiveInput>> inputsByTarget) {
        final Map<String, Directive> directives = new LinkedHashMap<>();
        final Set<String> paths = new LinkedHashSet<>(sourceByTarget.keySet());
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
