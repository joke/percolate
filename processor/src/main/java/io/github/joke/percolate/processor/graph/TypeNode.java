package io.github.joke.percolate.processor.graph;

import javax.lang.model.type.TypeMirror;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class TypeNode {
    private final TypeMirror type;

    @EqualsAndHashCode.Include
    private final String label;

    @Override
    public String toString() {
        return label;
    }
}
