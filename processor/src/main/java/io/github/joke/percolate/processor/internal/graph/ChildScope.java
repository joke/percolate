package io.github.joke.percolate.processor.internal.graph;

import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

// The element scope owned by a scope-owning Operation (a container element mapping): the per-element transform
// is a child plan with the same shape as a method body — an element input (declared, materialised lazily like
// any scope input) in, an element return-root Value out. The owning Operation is the only coupling between this
// scope and its parent; no Dep edge ever crosses the boundary.
//
// When the owning Operation lands, MapperGraph mints the return-root Value eagerly (it is the child plan's
// demand, folded into the owning Operation's cost) and records the element InputDecl. The element's LEAF Value
// is materialised lazily only if the child plan sources from it — an element mapped to a constant never mints
// one — while its binding (the lambda variable) is still emitted from the declaration. Both are set exactly
// once.
public final class ChildScope implements Scope {

    private final Operation owner;
    private final Scope parentScope;
    private @Nullable Value returnRoot;
    private @Nullable InputDecl elementInput;

    ChildScope(final Operation owner, final Scope parentScope) {
        this.owner = owner;
        this.parentScope = parentScope;
    }

    public Operation getOwner() {
        return owner;
    }

    // The element return-root: the child plan's demand, required SAT for the owning Operation to be SAT.
    public Value getReturnRoot() {
        return requireNonNull(returnRoot, "child scope roots are minted when the owning Operation lands");
    }

    // The element input declaration: base-case SAT within this scope, materialised lazily like a method parameter.
    public InputDecl getElementInput() {
        return requireNonNull(elementInput, "child scope roots are minted when the owning Operation lands");
    }

    void initialise(final Value newReturnRoot, final InputDecl newElementInput) {
        if (returnRoot != null || elementInput != null) {
            throw new IllegalStateException("child scope roots are set exactly once");
        }
        this.returnRoot = newReturnRoot;
        this.elementInput = newElementInput;
    }

    // The single element input declaration — already resolved, set when the owning Operation lands.
    @Override
    public Stream<InputDecl> inputDecls() {
        return Stream.of(getElementInput());
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
