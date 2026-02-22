package io.github.joke.percolate.spi;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;

public interface GenericMappingStrategy {

    boolean canHandle(TypeMirror source, TypeMirror target, ProcessingEnvironment env);

    GraphFragment expand(TypeMirror source, TypeMirror target, ProcessingEnvironment env);
}
