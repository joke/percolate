package io.github.joke.caffeinate.codegen.strategy;

import com.palantir.javapoet.CodeBlock;
import io.github.joke.caffeinate.resolution.ConverterRegistry;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;

public interface TypeMappingStrategy {

    /**
     * Returns true if this strategy can produce a converter for (source -> target) given the
     * converters already registered in the registry. Called during the fixpoint in ResolutionStage.
     */
    boolean canContribute(TypeMirror source, TypeMirror target, ConverterRegistry registry, ProcessingEnvironment env);

    /**
     * Generates a CodeBlock that converts {@code sourceExpr} (of type {@code source})
     * to {@code target}. Only called after {@code canContribute} returned true.
     */
    CodeBlock generate(
            String sourceExpr,
            TypeMirror source,
            TypeMirror target,
            ConverterRegistry registry,
            ProcessingEnvironment env);
}
