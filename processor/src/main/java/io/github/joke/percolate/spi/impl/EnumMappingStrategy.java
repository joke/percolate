package io.github.joke.percolate.spi.impl;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.spi.GenericMappingStrategy;
import io.github.joke.percolate.spi.GraphFragment;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * Handles enum-to-enum conversions using {@code TargetEnum.valueOf(sourceExpr.name())}.
 *
 * <p>No mapper method is needed; direct valueOf conversion is used.
 */
@AutoService(GenericMappingStrategy.class)
public final class EnumMappingStrategy implements GenericMappingStrategy {

    @Override
    public boolean canHandle(TypeMirror source, TypeMirror target, ProcessingEnvironment env) {
        return isEnumType(source) && isEnumType(target);
    }

    @Override
    public GraphFragment expand(TypeMirror source, TypeMirror target, ProcessingEnvironment env) {
        return new GraphFragment();
    }

    private static boolean isEnumType(TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return false;
        }
        return ((DeclaredType) type).asElement().getKind() == ElementKind.ENUM;
    }
}
