package io.github.joke.percolate.processor.transform;

import javax.lang.model.type.TypeMirror;
import lombok.Value;

@Value
public class TransformNode {
    TypeMirror inputType;
    TypeMirror outputType;
    TransformOperation operation;
}
