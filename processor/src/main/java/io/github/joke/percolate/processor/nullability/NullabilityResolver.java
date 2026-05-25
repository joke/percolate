package io.github.joke.percolate.processor.nullability;

import io.github.joke.percolate.spi.Nullability;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

public interface NullabilityResolver {
    Nullability resolve(TypeMirror type, Element scope);
}
