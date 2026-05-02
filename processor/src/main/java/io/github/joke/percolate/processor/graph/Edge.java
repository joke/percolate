package io.github.joke.percolate.processor.graph;

import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import lombok.Value;

@Value
public final class Edge implements Comparable<Edge> {
    Node from;
    Node to;
    int weight;
    Optional<AnnotationMirror> directive;

    @Override
    public int compareTo(final Edge other) {
        int c = this.from.id().compareTo(other.from.id());
        if (c != 0) {
            return c;
        }
        c = this.to.id().compareTo(other.to.id());
        if (c != 0) {
            return c;
        }
        c = Integer.compare(this.weight, other.weight);
        if (c != 0) {
            return c;
        }
        return Boolean.compare(this.directive.isPresent(), other.directive.isPresent());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Edge)) {
            return false;
        }
        final var other = (Edge) o;
        return this.from.equals(other.from)
                && this.to.equals(other.to)
                && this.weight == other.weight
                && Objects.equals(this.directive, other.directive);
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to, weight, directive);
    }
}
