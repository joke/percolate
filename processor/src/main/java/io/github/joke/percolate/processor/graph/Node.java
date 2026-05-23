package io.github.joke.percolate.processor.graph;

import lombok.Getter;

import java.util.Optional;
import javax.lang.model.type.TypeMirror;

@Getter
public final class Node implements Comparable<Node> {
    private static final String UNKNOWN_TYPE = "?";

    private Optional<TypeMirror> type;
    private final Location loc;
    private final Scope scope;
    private final Optional<Node> parent;

    public Node(final Optional<TypeMirror> type, final Location loc, final Scope scope) {
        this(type, loc, scope, Optional.empty());
    }

    public Node(final Optional<TypeMirror> type, final Location loc, final Scope scope, final Optional<Node> parent) {
        this.type = type;
        this.loc = loc;
        this.scope = scope;
        this.parent = parent;
    }

    public void setType(final TypeMirror newType) {
        if (type.isPresent()) {
            throw new IllegalStateException(
                    "Node.type is already set; setType() requires the current type to be empty");
        }
        this.type = Optional.of(newType);
    }

    public String id() {
        final String prefix =
                (loc instanceof ElementLocation) ? parent.map(Node::id).orElseGet(scope::encode) : scope.encode();
        return prefix + "::" + loc.segment() + "::" + typeEncode() + "@" + System.identityHashCode(this);
    }

    private String typeEncode() {
        return type.map(TypeMirror::toString).orElse(UNKNOWN_TYPE);
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
        return id().compareTo(other.id());
    }
}
