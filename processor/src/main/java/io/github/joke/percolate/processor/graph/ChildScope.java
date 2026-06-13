package io.github.joke.percolate.processor.graph;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The element scope owned by a scope-owning {@link Operation} (a container element mapping): the per-element
 * transform is a child plan with the same shape as a method body — an element param-root {@link Value} in,
 * an element return-root {@link Value} out. The owning Operation is the only coupling between this scope and
 * its parent; no {@link Dep} edge ever crosses the boundary.
 *
 * <p>The two roots are minted by {@code MapperGraph} when the owning Operation lands and set exactly once.
 */
public final class ChildScope implements Scope {

    private final Operation owner;
    private final Scope parentScope;
    private @Nullable Value paramRoot;
    private @Nullable Value returnRoot;

    ChildScope(final Operation owner, final Scope parentScope) {
        this.owner = owner;
        this.parentScope = parentScope;
    }

    public Operation getOwner() {
        return owner;
    }

    /** The element param-root: base-case SAT within this scope, like a method parameter. */
    public Value getParamRoot() {
        return requireNonNull(paramRoot, "child scope roots are minted when the owning Operation lands");
    }

    /** The element return-root: the child plan's demand, required SAT for the owning Operation to be SAT. */
    public Value getReturnRoot() {
        return requireNonNull(returnRoot, "child scope roots are minted when the owning Operation lands");
    }

    void setRoots(final Value newParamRoot, final Value newReturnRoot) {
        if (paramRoot != null || returnRoot != null) {
            throw new IllegalStateException("child scope roots are set exactly once");
        }
        this.paramRoot = newParamRoot;
        this.returnRoot = newReturnRoot;
    }

    @Override
    public String encode() {
        return owner.id() + "::elem";
    }

    @Override
    public Optional<Scope> parent() {
        return Optional.of(parentScope);
    }
}
