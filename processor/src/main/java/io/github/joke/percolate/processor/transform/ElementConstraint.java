package io.github.joke.percolate.processor.transform;

import javax.lang.model.type.TypeMirror;
import lombok.Value;

@Value
public class ElementConstraint {
    TypeMirror fromType;
    TypeMirror toType;
}
