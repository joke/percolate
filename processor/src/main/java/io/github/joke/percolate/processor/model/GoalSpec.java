package io.github.joke.percolate.processor.model;

import io.github.joke.percolate.spi.Constraint;
import io.github.joke.percolate.spi.Directive;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Value;

// The declared-bindings goal spec for one mapper method — the goal half of the planning problem (design D6/D9),
// assembled generically from whatever a io.github.joke.percolate.spi.DirectiveReader declared through a
// DirectiveSink (design D7 of change decouple-engine-from-strategy-semantics): every Bind contributes its next
// path segment as a declared child at its parent level, and the full target path maps to a Directive assembled
// from that binding's source path (if any) plus every input attached at the same path — present even when no
// bind ever touched the path (e.g. a @MapEnum table, attached only at the empty root path).
//
// .declaredChildren(String) gates assembly at a target level (a constructor is a candidate iff its parameter-
// name set equals the declared-children set); .bindingFor(String) is the Directive a target Value's demand
// carries; .constraintsFor(String) is the demand-scoped admissibility constraints a reader attached there
// (design D8). A directive never lives on a Value; the goal spec travels with the demand context.
@Value
public class GoalSpec {

    // Parent dotted path -> declared child names at that level (insertion-ordered for determinism).
    Map<String, Set<String>> childrenByLevel;

    // Exact dotted target path -> the Directive assembled there.
    Map<String, Directive> directiveByTarget;

    // Exact dotted target path -> the demand-scoped Constraints a reader attached there.
    Map<String, List<Constraint>> constraintsByTarget;

    // Every scopeInput override a reader published for this method (design D5/D7), in declaration order.
    List<ScopeInputOverride> scopeInputOverrides;

    // The declared child names at parentPath (empty when the level declares nothing).
    public Set<String> declaredChildren(final String parentPath) {
        return childrenByLevel.getOrDefault(parentPath, Set.of());
    }

    // The Directive assembled at the exact targetPath, or empty when the path is purely structural.
    public Optional<Directive> bindingFor(final String targetPath) {
        return Optional.ofNullable(directiveByTarget.get(targetPath));
    }

    // The demand-scoped constraints attached at the exact targetPath (empty when none were attached).
    public List<Constraint> constraintsFor(final String targetPath) {
        return constraintsByTarget.getOrDefault(targetPath, List.of());
    }

    // The empty goal spec: no bindings, no inputs, no constraints, no scope-input overrides.
    public static GoalSpec empty() {
        return new GoalSpec(Map.of(), Map.of(), Map.of(), List.of());
    }
}
