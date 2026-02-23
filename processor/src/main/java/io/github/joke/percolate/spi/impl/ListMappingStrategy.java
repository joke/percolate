package io.github.joke.percolate.spi.impl;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.spi.GenericMappingStrategy;
import io.github.joke.percolate.spi.GraphFragment;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * Handles {@code List<A>} to {@code List<B>} conversions by mapping each element via a converter
 * method.
 *
 * <p>Generated code pattern: {@code sourceExpr.stream().map(this::converter).collect(Collectors.toList())}
 */
@AutoService(GenericMappingStrategy.class)
public final class ListMappingStrategy implements GenericMappingStrategy {

    @Override
    public boolean canHandle(TypeMirror source, TypeMirror target, ProcessingEnvironment env) {
        return isListType(source) && isListType(target);
    }

    @Override
    public GraphFragment expand(TypeMirror source, TypeMirror target, ProcessingEnvironment env) {
        return new GraphFragment();
    }

    private static boolean isListType(TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return false;
        }
        String erasedName = ((DeclaredType) type).asElement().toString();
        return "java.util.List".equals(erasedName);
    }
}
