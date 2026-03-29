package io.github.joke.percolate.processor.graph;

import java.util.Objects;
import javax.lang.model.type.TypeMirror;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public final class TypeNode {
    private final TypeMirror type;
    private final String label;

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TypeNode)) {
            return false;
        }
        return Objects.equals(label, ((TypeNode) o).label);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(label);
    }

    @Override
    public String toString() {
        return label;
    }
}
