package io.github.joke.percolate.spi.impl;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.spi.GenericMappingStrategy;
import io.github.joke.percolate.spi.GraphFragment;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * Handles wrapping a non-Optional source into an {@code Optional<T>} target.
 *
 * <p>Generated code pattern: {@code java.util.Optional.ofNullable(this.converter(sourceExpr))} when
 * a converter exists, or {@code java.util.Optional.ofNullable(sourceExpr)} for direct wrapping.
 */
@AutoService(GenericMappingStrategy.class)
public final class OptionalMappingStrategy implements GenericMappingStrategy {

    @Override
    public boolean canHandle(TypeMirror source, TypeMirror target, ProcessingEnvironment env) {
        return isOptionalType(target) && !isOptionalType(source);
    }

    @Override
    public GraphFragment expand(TypeMirror source, TypeMirror target, ProcessingEnvironment env) {
        return new GraphFragment();
    }

    private static boolean isOptionalType(TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return false;
        }
        String erasedName = ((DeclaredType) type).asElement().toString();
        return "java.util.Optional".equals(erasedName);
    }
}
