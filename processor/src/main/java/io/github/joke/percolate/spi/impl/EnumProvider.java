package io.github.joke.percolate.spi.impl;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.spi.ConversionFragment;
import io.github.joke.percolate.spi.ConversionProvider;
import io.github.joke.percolate.stage.MethodRegistry;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

@AutoService(ConversionProvider.class)
public final class EnumProvider implements ConversionProvider {

    @Override
    public boolean canHandle(
            final TypeMirror source,
            final TypeMirror target,
            final MethodRegistry registry,
            final ProcessingEnvironment env) {
        return canConvertEnums(source, target);
    }

    @Override
    public ConversionFragment provide(
            final TypeMirror source,
            final TypeMirror target,
            final MethodRegistry registry,
            final ProcessingEnvironment env) {
        return ConversionFragment.of();
    }

    public static boolean canConvertEnums(final TypeMirror source, final TypeMirror target) {
        return isEnumType(source) && isEnumType(target);
    }

    private static boolean isEnumType(final TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return false;
        }
        return ((DeclaredType) type).asElement().getKind() == ElementKind.ENUM;
    }
}
