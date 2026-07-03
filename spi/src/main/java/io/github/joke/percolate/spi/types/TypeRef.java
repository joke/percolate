package io.github.joke.percolate.spi.types;

import java.util.List;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * An immutable type reference — the owned type currency (change {@code evict-javax-model}, design D2). A
 * {@code TypeRef} is a plain value: deep immutability, value equality (usable as a map key — the graph's dedup
 * identity), and a {@link #toString()} that renders the Java source form. It holds no handle into any compiler
 * session; the type-system <em>questions</em> (sameness, assignability, erasure, boxing) are answered by the
 * {@link TypeSpace} snapshot, never by the ref itself.
 *
 * <p>A free type variable is a first-class {@link Variable} leaf — the shape {@code javax.lang.model} cannot
 * fabricate (see {@code PortType}'s workaround, which this model dissolves).
 */
// Intentional pseudo-sealed base, mirroring PortType: the five leaves below are the only permitted shapes (a
// package-private constructor pins membership) and consumers walk them structurally — Java 11 has no `sealed`,
// so the closed hierarchy is enforced by convention.
@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
public abstract class TypeRef {

    /** Package-private to keep the shape pseudo-sealed: only the leaves below participate. */
    TypeRef() {}

    /** A declared reference, e.g. {@code java.lang.String} or {@code java.util.List<java.lang.String>}. */
    public static TypeRef declared(final String qualifiedName, final TypeRef... args) {
        return new Declared(qualifiedName, List.of(args));
    }

    /** A declared reference over an argument list (defensively copied). */
    public static TypeRef declared(final String qualifiedName, final List<TypeRef> args) {
        return new Declared(qualifiedName, List.copyOf(args));
    }

    /** A primitive reference, e.g. {@code int}. */
    public static TypeRef primitive(final PrimitiveKind kind) {
        return new Primitive(kind);
    }

    /** An array reference over a component, e.g. {@code java.lang.String[]}. */
    public static TypeRef array(final TypeRef component) {
        return new Array(component);
    }

    /** A free type variable, e.g. {@code A} — grounded by {@link TypeSpace#match(TypeRef, TypeRef)}. */
    public static TypeRef variable(final String name) {
        return new Variable(name);
    }

    /** The absent type ({@code void} returns, none). */
    public static TypeRef none() {
        return None.INSTANCE;
    }

    /** A declared reference: qualified name over ordered type arguments (empty for a raw/non-generic type). */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Declared extends TypeRef {
        String qualifiedName;
        List<TypeRef> args;

        @Override
        public String toString() {
            if (args.isEmpty()) {
                return qualifiedName;
            }
            return args.stream().map(TypeRef::toString).collect(Collectors.joining(", ", qualifiedName + "<", ">"));
        }
    }

    /** A primitive reference. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Primitive extends TypeRef {
        PrimitiveKind kind;

        @Override
        public String toString() {
            return kind.getSourceName();
        }
    }

    /** An array reference. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Array extends TypeRef {
        TypeRef component;

        @Override
        public String toString() {
            return component + "[]";
        }
    }

    /** A free type variable identified by name. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class Variable extends TypeRef {
        String name;

        @Override
        public String toString() {
            return name;
        }
    }

    /** The absent type. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static class None extends TypeRef {
        private static final None INSTANCE = new None();

        @Override
        public String toString() {
            return "void";
        }
    }
}
