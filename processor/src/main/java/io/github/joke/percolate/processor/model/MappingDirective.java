package io.github.joke.percolate.processor.model;

import lombok.Value;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;

@Value
public class MappingDirective {
    String target;
    String source;
    AnnotationMirror mirror;
    AnnotationValue targetValue;
    AnnotationValue sourceValue;
}
