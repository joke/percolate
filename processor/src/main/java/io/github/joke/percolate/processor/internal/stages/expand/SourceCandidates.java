package io.github.joke.percolate.processor.internal.stages.expand;

import io.github.joke.percolate.processor.internal.graph.AddValue;
import io.github.joke.percolate.processor.internal.graph.InputDecl;
import io.github.joke.percolate.processor.internal.graph.Location;
import io.github.joke.percolate.processor.internal.graph.MapperGraph;
import io.github.joke.percolate.processor.internal.graph.Scope;
import io.github.joke.percolate.processor.internal.graph.Value;
import io.github.joke.percolate.processor.internal.graph.Visibility;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import static io.github.joke.percolate.spi.Nullability.NON_NULL;
import static io.github.joke.percolate.spi.Nullability.NULLABLE;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Stream.concat;

// Source-binding lookup for the expansion driver (demand-driven-expansion D4): given a scope, the in-scope
// source Value that can feed a demanded Port — an already-materialised graph source first, else a matching
// scope input declaration (Scope.inputDecls) materialised on demand as a LEAF through the Applier (inputs are
// declared, not pre-seeded). It also exposes the in-scope source types (declared inputs plus discovered graph
// sources) that grounding-by-match unifies a type-variable port against — never a strategy-facing candidate
// snapshot (the engine sources inputs). The path is uniform across scope kinds: a method parameter and a
// container element root are both just input declarations, with no instanceof test. A cohesive collaborator the
// work-list driver delegates to, so the driver stays the work-list dispatch + Operation landing site.
@RequiredArgsConstructor
final class SourceCandidates {

    private final MapperGraph graph;
    private final Applier applier;
    private final ResolveCtx resolveCtx;

    // The in-scope source types — declared inputs plus discovered graph sources — that grounding-by-match unifies a
    // type-variable port against. The declared input types are available without materialising any Value, so a
    // template port grounds without the (producer-facing) candidate snapshot.
    //
    // Deterministic by construction (graph-expansion "Type-matched source selection SHALL be deterministic"):
    // declared inputs precede discovered graph sources, each in a stable order — Scope.inputDecls streams an
    // ordered List in declaration order, and MapperGraph.valuesIn is sorted by Value.id. A same-typed pair of
    // parameters is therefore always offered to BindingEnumerator in declaration order, so grounding-by-match over-
    // emits and the extraction fold prunes ties in that same order.
    List<TypeMirror> sourceTypes(final Scope scope) {
        return concat(
                        scope.inputDecls().map(InputDecl::getType),
                        sourceValues(scope).map(Value::type))
                .collect(toUnmodifiableList());
    }

    // The in-scope source Value that can feed port, ranked: a matching directive-pinnedSource first (so a same-
    // typed sibling can never shadow it), then an already-materialised graph source of least id, else the first
    // matching scope input declaration materialised on demand as a LEAF (idempotent through the dedup index).
    // pinnedSource is null when the demand carries no directive source path.
    @Nullable
    Value matchingSource(final Scope scope, final Port port, final @Nullable Value pinnedSource) {
        return pinnedMatch(pinnedSource, port)
                .or(() -> existingMatch(scope, port))
                .orElseGet(() -> materialiseMatchingInput(scope, port));
    }

    Optional<Value> pinnedMatch(final @Nullable Value pinnedSource, final Port port) {
        return Optional.ofNullable(pinnedSource).filter(value -> matchesPort(value, port));
    }

    Optional<Value> existingMatch(final Scope scope, final Port port) {
        return sourceValues(scope).filter(value -> matchesPort(value, port)).min(comparing(Value::id));
    }

    // Whether value can feed port: same type and a non-null source satisfies any nullness.
    boolean matchesPort(final Value value, final Port port) {
        return matches(value.type(), value.nullness(), port);
    }

    // Deterministic by construction: Scope.inputDecls streams the scope's input declarations in declaration order
    // (an ordered List, never a hash-ordered collection), so findFirst() always selects the earlier-declared match
    // when two declarations — e.g. two same-typed parameters — both fit.
    @Nullable
    Value materialiseMatchingInput(final Scope scope, final Port port) {
        return scope.inputDecls()
                .filter(decl -> matches(decl.getType(), decl.getNullness(), port))
                .findFirst()
                .map(decl -> applier.apply(
                        graph, new AddValue(scope, decl.getLocation(), decl.getType(), decl.getNullness())))
                .orElse(null);
    }

    // The named Value feeding a BY_NAME port: the declaration named port.getBindingName() in the scope's own
    // declarations, or failing that in the nearest ancestor scope declaring it Visibility.INHERITED (design D5) —
    // materialised at that declaration's own location, but in the requesting scope: a Dep edge never crosses a
    // scope boundary, so a descendant scope consuming an inherited binding gets its own Value at the same location,
    // not the declaring scope's. null when the name is unbound anywhere in the chain, or when it is bound but the
    // binding's type is not assignable to the port's declared type (design Decision 2: verified, not encoded into
    // the name). Either failure is reported by PortSourceResolver's REQUIRE handling — the engine's own concern,
    // not this collaborator's.
    @Nullable
    Value byNameSource(final Scope scope, final Port port) {
        return byNameDecl(scope, port.getBindingName())
                .filter(decl -> resolveCtx.isAssignable(decl.getType(), port.getType()))
                .map(decl -> applier.apply(
                        graph, new AddValue(scope, decl.getLocation(), decl.getType(), decl.getNullness())))
                .orElse(null);
    }

    // The declared type of the binding named port.getBindingName(), for a REQUIRE-miss mismatch message.
    Optional<TypeMirror> byNameDeclaredType(final Scope scope, final Port port) {
        return byNameDecl(scope, port.getBindingName()).map(InputDecl::getType);
    }

    // scope's own declaration named name, else the nearest ancestor's INHERITED one.
    Optional<InputDecl> byNameDecl(final Scope scope, final String name) {
        return ownNamedDecl(scope, name).or(() -> inheritedAncestorDecl(scope, name));
    }

    Optional<InputDecl> ownNamedDecl(final Scope scope, final String name) {
        return scope.inputDecls().filter(decl -> decl.getName().equals(name)).findFirst();
    }

    // Walks scope's ancestor chain for the nearest INHERITED declaration named name.
    Optional<InputDecl> inheritedAncestorDecl(final Scope scope, final String name) {
        return scope.parent()
                .flatMap(parent -> ownInheritedDecl(parent, name).or(() -> inheritedAncestorDecl(parent, name)));
    }

    Optional<InputDecl> ownInheritedDecl(final Scope scope, final String name) {
        return scope.inputDecls()
                .filter(decl -> decl.getVisibility() == Visibility.INHERITED
                        && decl.getName().equals(name))
                .findFirst();
    }

    Stream<Value> sourceValues(final Scope scope) {
        return graph.valuesIn(scope).filter(value -> {
            final var role = value.getLoc().role();
            return role == Location.Role.ACCESS || role == Location.Role.LEAF;
        });
    }

    // Whether a source of (type, nullness) can feed port: same type, non-null satisfies any.
    boolean matches(final TypeMirror sourceType, final Nullability sourceNullness, final Port port) {
        final var nullnessClash = port.getNullness() == NON_NULL && sourceNullness == NULLABLE;
        return !nullnessClash && resolveCtx.isSameType(sourceType, port.getType());
    }
}
