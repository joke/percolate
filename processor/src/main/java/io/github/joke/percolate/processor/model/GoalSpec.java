package io.github.joke.percolate.processor.model;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Value;

/**
 * The declared-bindings goal spec for one mapper method — the goal half of the planning problem (design D6/D9).
 * It is derived by grouping the method's {@code @Map} directives by dotted target-path level: every prefix of a
 * directive's target contributes its next segment as a declared child at that level, and the full target path
 * maps to the leaf directive itself. So {@code @Map(target="address.street")} contributes {@code address} at the
 * root level (key {@code ""}) and {@code street} at the {@code address} level, and binds the leaf directive at
 * {@code address.street}.
 *
 * <p>{@link #declaredChildren(String)} gates assembly at a target level (a constructor is a candidate iff its
 * parameter-name set equals the declared-children set); {@link #bindingFor(String)} is the leaf directive a
 * target Value's demand carries — its source path drives descent, its {@code constant}/{@code defaultValue}
 * participate. A directive never lives on a {@code Value}; the goal spec travels with the demand context.
 */
@Value
public class GoalSpec {

    /** Parent dotted path -> declared child names at that level (insertion-ordered for determinism). */
    Map<String, Set<String>> childrenByLevel;

    /** Exact dotted target path -> the leaf {@code @Map} directive bound there. */
    Map<String, MappingDirective> directiveByTarget;

    /** The declared child names at {@code parentPath} (empty when the level declares nothing). */
    public Set<String> declaredChildren(final String parentPath) {
        return childrenByLevel.getOrDefault(parentPath, Set.of());
    }

    /** The leaf directive bound at the exact {@code targetPath}, or empty when the path is purely structural. */
    public Optional<MappingDirective> bindingFor(final String targetPath) {
        return Optional.ofNullable(directiveByTarget.get(targetPath));
    }

    /** Derives the per-level goal spec from a method's validated directives (design D9). */
    @SuppressWarnings("PMD.UseConcurrentHashMap") // single-threaded per-method derivation; insertion order matters
    public static GoalSpec from(final List<MappingDirective> directives) {
        final Map<String, Set<String>> levels = new LinkedHashMap<>();
        final Map<String, MappingDirective> bindings = new LinkedHashMap<>();
        for (final var directive : directives) {
            final var segments = splitPath(directive.getTarget());
            bindings.put(String.join(".", segments), directive);
            for (var i = 0; i < segments.size(); i++) {
                final var parent = String.join(".", segments.subList(0, i));
                levels.computeIfAbsent(parent, key -> new LinkedHashSet<>()).add(segments.get(i));
            }
        }
        return new GoalSpec(levels, bindings);
    }

    private static List<String> splitPath(final String path) {
        if (path == null || path.isEmpty()) {
            return List.of();
        }
        return List.of(path.split("\\.", -1));
    }
}
