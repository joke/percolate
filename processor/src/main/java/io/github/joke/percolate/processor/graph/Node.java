package io.github.joke.percolate.processor.graph;

import java.util.Optional;
import javax.lang.model.type.TypeMirror;

public final class Node implements Comparable<Node> {
    private final Optional<TypeMirror> type;
    private final Location loc;
    private final Scope scope;

    public Node(final Optional<TypeMirror> type, final Location loc, final Scope scope) {
        this.type = type;
        this.loc = loc;
        this.scope = scope;
    }

    public Optional<TypeMirror> getType() {
        return type;
    }

    public Location getLoc() {
        return loc;
    }

    public Scope getScope() {
        return scope;
    }

    public String id() {
        return "node@" + System.identityHashCode(this);
    }

    @Override
    public boolean equals(final Object o) {
        return this == o;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public int compareTo(final Node other) {
        return Integer.compare(System.identityHashCode(this), System.identityHashCode(other));
    }
}
