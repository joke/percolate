package io.github.joke.percolate.spi;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;

public interface ObjectCreationStrategy {

    boolean canCreate(TypeElement type, ProcessingEnvironment env);

    CreationDescriptor describe(TypeElement type, ProcessingEnvironment env);
}
