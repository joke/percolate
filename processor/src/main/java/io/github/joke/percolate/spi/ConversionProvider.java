package io.github.joke.percolate.spi;

import io.github.joke.percolate.stage.MethodRegistry;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;

public interface ConversionProvider {

    boolean canHandle(TypeMirror source, TypeMirror target, ProcessingEnvironment env);

    /**
     * Returns a ConversionFragment — the nodes to insert between source and target.
     * Providers may register auto-generated helper methods in the registry.
     * Inline providers (Optional, Boxing, Collection iteration) do NOT register methods.
     */
    ConversionFragment provide(
            TypeMirror source, TypeMirror target, MethodRegistry registry, ProcessingEnvironment env);
}
