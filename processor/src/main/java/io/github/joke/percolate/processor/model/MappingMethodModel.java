package io.github.joke.percolate.processor.model;

import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import lombok.Value;

@Value
public class MappingMethodModel {
    ExecutableElement method;
    TypeMirror sourceType;
    TypeMirror targetType;
    List<MapDirective> directives;
}
