package io.github.joke.percolate.spi.types;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An immutable snapshot of a finite type universe: declarations keyed by qualified name, plus the type algebra
 * the engine and strategies ask of it (design D3). A {@code TypeSpace} is <b>a value passed through
 * {@code ResolveCtx}</b> — never ambient static state — so there is nothing shared to race on; parallel specs and
 * threaded pitest are safe by construction.
 *
 * <p>The algebra is deliberately <b>lawful, not faithful</b> (no wildcard calculus, no capture): sameness is
 * value equality; erasure drops arguments; assignability is a reflexive-transitive walk over declared supertype
 * edges with type-argument substitution, invariant arguments at equal heads, and raw-target fallback; boxing is
 * the fixed {@link PrimitiveKind} table. Real Java semantics remain the feature-e2e layer's job.
 */
public final class TypeSpace {

    private final Map<String, TypeDecl> decls;

    private TypeSpace(final Map<String, TypeDecl> decls) {
        this.decls = decls;
    }

    /** A snapshot over the given declarations (leaf types without edges need no declaration). */
    public static TypeSpace of(final TypeDecl... decls) {
        return new TypeSpace(Stream.of(decls)
                .collect(Collectors.toUnmodifiableMap(TypeDecl::getQualifiedName, Function.identity())));
    }

    /** The declaration named {@code qualifiedName}, or empty for an undeclared (edge-less leaf) type. */
    public Optional<TypeDecl> decl(final String qualifiedName) {
        return Optional.ofNullable(decls.get(qualifiedName));
    }

    /** Structural sameness — value equality of the two refs (invariant arguments, recursively). */
    public boolean isSameType(final TypeRef a, final TypeRef b) {
        return a.equals(b);
    }

    /** The erasure of {@code type}: a declared ref without arguments; an array of the erased component. */
    public TypeRef erasure(final TypeRef type) {
        if (type instanceof TypeRef.Declared) {
            return TypeRef.declared(((TypeRef.Declared) type).getQualifiedName());
        }
        if (type instanceof TypeRef.Array) {
            return TypeRef.array(erasure(((TypeRef.Array) type).getComponent()));
        }
        return type;
    }

    /**
     * Whether a value of type {@code from} is assignable to {@code to}: reflexive, then a walk of {@code from}'s
     * declared supertype edges with argument substitution. Equal heads compare arguments invariantly; a raw
     * {@code to} accepts any parameterisation of its head. Primitives are identity-only (widening is a
     * conversion-strategy concern, not an assignability fact); refs containing free variables never match.
     */
    public boolean isAssignable(final TypeRef from, final TypeRef to) {
        if (from.equals(to)) {
            return true;
        }
        if (from instanceof TypeRef.Declared && to instanceof TypeRef.Declared) {
            return declaredAssignable((TypeRef.Declared) from, (TypeRef.Declared) to);
        }
        return false;
    }

    /** The wrapper reference for a primitive kind ({@code int → java.lang.Integer}). */
    public TypeRef boxed(final PrimitiveKind kind) {
        return TypeRef.declared(kind.getBoxedName());
    }

    /** The primitive kind a wrapper reference unboxes to, or empty for a non-wrapper ref. */
    public Optional<PrimitiveKind> unboxed(final TypeRef type) {
        if (!(type instanceof TypeRef.Declared)
                || !((TypeRef.Declared) type).getArgs().isEmpty()) {
            return Optional.empty();
        }
        return PrimitiveKind.ofBoxedName(((TypeRef.Declared) type).getQualifiedName());
    }

    /**
     * One-way structural unification of a {@code pattern} (which may contain {@link TypeRef.Variable}s) against a
     * {@code concrete} ref: the functor-lift grounding primitive ({@code Stream<A>} vs {@code Stream<Human>}
     * yields {@code A → Human}). Returns the variable bindings, or empty when the shapes do not match (including
     * a variable matched against two different refs).
     */
    public Optional<Map<String, TypeRef>> match(final TypeRef pattern, final TypeRef concrete) {
        final var bindings = new HashMap<String, TypeRef>();
        return matchInto(pattern, concrete, bindings) ? Optional.of(Map.copyOf(bindings)) : Optional.empty();
    }

    /**
     * Rebuild a concrete ref from a {@code pattern} by substituting {@link TypeRef.Variable}s with their
     * {@code bindings} — the counterpart of {@link #match(TypeRef, TypeRef)} (the engine's {@code ground}).
     * An unbound variable is left in place.
     */
    public TypeRef ground(final TypeRef pattern, final Map<String, TypeRef> bindings) {
        return substitute(pattern, bindings);
    }

    private boolean declaredAssignable(final TypeRef.Declared from, final TypeRef.Declared to) {
        if (from.getQualifiedName().equals(to.getQualifiedName())) {
            return to.getArgs().isEmpty();
        }
        return substitutedSuperEdges(from).anyMatch(edge -> isAssignable(edge, to));
    }

    /** {@code from}'s declared supertype edges with {@code from}'s actual arguments substituted along each. */
    private Stream<TypeRef> substitutedSuperEdges(final TypeRef.Declared from) {
        return decl(from.getQualifiedName()).stream().flatMap(decl -> decl.getSuperEdges().stream()
                .map(edge -> substitute(edge, parameterBindings(decl, from))));
    }

    /** The declaration's type parameters bound to the use-site's arguments (empty for a raw use-site). */
    private static Map<String, TypeRef> parameterBindings(final TypeDecl decl, final TypeRef.Declared from) {
        final var parameters = decl.getTypeParameters();
        if (parameters.size() != from.getArgs().size()) {
            return Map.of();
        }
        final var bindings = new HashMap<String, TypeRef>();
        for (int i = 0; i < parameters.size(); i++) {
            bindings.put(parameters.get(i), from.getArgs().get(i));
        }
        return Map.copyOf(bindings);
    }

    private static TypeRef substitute(final TypeRef type, final Map<String, TypeRef> bindings) {
        if (type instanceof TypeRef.Variable) {
            final var bound = bindings.get(((TypeRef.Variable) type).getName());
            return bound != null ? bound : type;
        }
        if (type instanceof TypeRef.Declared) {
            final var declared = (TypeRef.Declared) type;
            final List<TypeRef> args = declared.getArgs().stream()
                    .map(arg -> substitute(arg, bindings))
                    .collect(Collectors.toUnmodifiableList());
            return TypeRef.declared(declared.getQualifiedName(), args);
        }
        if (type instanceof TypeRef.Array) {
            return TypeRef.array(substitute(((TypeRef.Array) type).getComponent(), bindings));
        }
        return type;
    }

    private static boolean matchInto(
            final TypeRef pattern, final TypeRef concrete, final Map<String, TypeRef> bindings) {
        if (pattern instanceof TypeRef.Variable) {
            final var name = ((TypeRef.Variable) pattern).getName();
            final var existing = bindings.putIfAbsent(name, concrete);
            return existing == null || existing.equals(concrete);
        }
        if (pattern instanceof TypeRef.Declared && concrete instanceof TypeRef.Declared) {
            return declaredMatch((TypeRef.Declared) pattern, (TypeRef.Declared) concrete, bindings);
        }
        if (pattern instanceof TypeRef.Array && concrete instanceof TypeRef.Array) {
            return matchInto(
                    ((TypeRef.Array) pattern).getComponent(), ((TypeRef.Array) concrete).getComponent(), bindings);
        }
        return pattern.equals(concrete);
    }

    private static boolean declaredMatch(
            final TypeRef.Declared pattern, final TypeRef.Declared concrete, final Map<String, TypeRef> bindings) {
        if (!pattern.getQualifiedName().equals(concrete.getQualifiedName())
                || pattern.getArgs().size() != concrete.getArgs().size()) {
            return false;
        }
        for (int i = 0; i < pattern.getArgs().size(); i++) {
            if (!matchInto(pattern.getArgs().get(i), concrete.getArgs().get(i), bindings)) {
                return false;
            }
        }
        return true;
    }
}
