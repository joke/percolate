package io.github.joke.percolate.processor.model;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import lombok.Value;

@Value
public class MappingDirective {
    String target;
    String source;
    AnnotationMirror mirror;
    AnnotationValue targetValue;
    AnnotationValue sourceValue;
}
