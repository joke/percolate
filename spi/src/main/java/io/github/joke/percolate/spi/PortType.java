package io.github.joke.percolate.spi;

import java.util.List;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * The structural shape of a {@link Port} type that may carry a <b>type variable</b> (design D2, change
 * {@code target-driven-engine} § Spike Findings 1.2). A concrete port type is a {@link Concrete} leaf; a generic
 * functor-lift input port {@code F<A>} is an {@link App} over a {@link Var}. The engine grounds a variable by
 * <em>matching</em> a {@code PortType} against an in-scope concrete source ({@code unify}), then rebuilds a concrete
 * {@link TypeMirror} ({@code ground}) — never by demanding an abstract type.
 *
 * <p>It is deliberately <b>not</b> a {@link TypeMirror}: {@code javax.lang.model} cannot fabricate a free type
 * variable (it only exposes a {@link TypeElement}'s own bound parameters, and {@code Types.getDeclaredType} demands
 * concrete arguments), so the template is carried as plain structural data the engine knows how to unify and ground.
 * The mechanic that walks this shape lives in the engine; {@code PortType} itself names no container or conversion
 * kind.
 */
// Intentional pseudo-sealed base: the three leaves below are the only permitted shapes (a package-private
// constructor pins membership), and the engine walks them structurally rather than through a dispatch method —
// Java 11 has no `sealed`, so the closed hierarchy is enforced by convention, not an abstract method.
@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
public abstract class PortType {

    /** Package-private to keep the shape pseudo-sealed: only the leaves below participate. */
    PortType() {}

    /** A fully-known leaf, e.g. {@code PersonView} — the common, backward-compatible case. */
    public static PortType concrete(final TypeMirror type) {
        return new Concrete(type);
    }

    /** An unbound variable slot, e.g. {@code A} — grounded by matching against a concrete source. */
    public static PortType variable(final int index) {
        return new Var(index);
    }

    /** A parameterised application, e.g. {@code Set<…>} / {@code Flux<…>}, whose arguments may be variables. */
    public static PortType app(final TypeElement erasure, final List<PortType> args) {
        return new App(erasure, List.copyOf(args));
    }

    /** A fully-known leaf type. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static final class Concrete extends PortType {
        TypeMirror type;
    }

    /** An unbound variable slot identified by an index local to the owning {@link OperationSpec}. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static final class Var extends PortType {
        int index;
    }

    /** A parameterised application: a top-level {@code erasure} ({@link TypeElement}) over ordered argument shapes. */
    @Value
    @EqualsAndHashCode(callSuper = false)
    public static final class App extends PortType {
        TypeElement erasure;
        List<PortType> args;
    }
}
