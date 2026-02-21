package io.github.joke.caffeinate.codegen.strategy;

import com.palantir.javapoet.CodeBlock;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

public interface TypeMappingStrategy {
    boolean supports(TypeMirror source, TypeMirror target, ProcessingEnvironment env);

    /**
     * Returns true if this strategy can generate code without a converter method reference.
     * Strategies that support identity (no-op) conversion override this to return true.
     * Default is false — a converter ref is required.
     */
    default boolean supportsIdentity(TypeMirror source, TypeMirror target, ProcessingEnvironment env) {
        return false;
    }

    CodeBlock generate(
            String sourceExpr,
            TypeMirror source,
            TypeMirror target,
            @Nullable String converterMethodRef,
            ProcessingEnvironment env);
}
