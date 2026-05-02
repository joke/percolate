package io.github.joke.percolate.processor.graph;

import java.util.Objects;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;
import lombok.Value;

@Value
public final class Node implements Comparable<Node> {
    Optional<TypeMirror> type;
    Location loc;
    Scope scope;

    public String id() {
        final var typeStr = type.map(TypeMirror::toString).orElse("?");
        final var locStr = loc != null ? loc.encode() : "none";
        return scope.encode() + "::" + locStr + "::" + typeStr;
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
        return Objects.equals(this.id(), other.id());
    }

    @Override
    public int hashCode() {
        return Objects.hash(id());
    }
}
