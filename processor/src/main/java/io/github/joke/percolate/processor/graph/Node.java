package io.github.joke.percolate.processor.graph;

import lombok.Value;

import javax.lang.model.type.TypeMirror;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

@Value
public final class Node implements Comparable<Node> {
    Optional<TypeMirror> type;
    Location loc;
    Scope scope;
    Optional<Node> parent;

    private String typeEncode() {
        return type.map(TypeMirror::toString).orElse("?");
    }

    public String id() {
        if (loc instanceof ElementLocation) {
            return parent.orElseThrow().id() + "::" + loc.segment() + "::" + typeEncode();
        }
        final var seg = loc != null ? loc.segment() : "none";
        return scope.encode() + "::" + seg + "::" + typeEncode();
    }

    @Override
    public int compareTo(final Node other) {
        return this.id().compareTo(other.id());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Node)) {
            return false;
        }
        final var other = (Node) o;
        try {
            return Objects.equals(this.id(), other.id());
        } catch (final NoSuchElementException e) {
            return Objects.equals(this.parent, other.parent)
                    && Objects.equals(this.type, other.type)
                    && Objects.equals(this.loc, other.loc)
                    && Objects.equals(this.scope, other.scope);
        }
    }

    @Override
    public int hashCode() {
        try {
            return Objects.hash(id());
        } catch (final NoSuchElementException e) {
            return Objects.hash(type, loc, scope, parent);
        }
    }
}
