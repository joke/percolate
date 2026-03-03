package io.github.joke.percolate.spi;

import io.github.joke.percolate.stage.MethodRegistry;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;

public interface ConversionProvider {

    boolean canHandle(TypeMirror source, TypeMirror target, MethodRegistry registry, ProcessingEnvironment env);

    /**
     * Returns a ConversionFragment — the nodes to insert between source and target.
     * Providers may register auto-generated helper methods in the registry.
     * Inline providers (Optional, Boxing, Collection iteration) do NOT register methods.
     */
    ConversionFragment provide(
            TypeMirror source, TypeMirror target, MethodRegistry registry, ProcessingEnvironment env);

    /** Lower number = higher priority. Default is 100. */
    default int priority() {
        return 100;
    }
}
