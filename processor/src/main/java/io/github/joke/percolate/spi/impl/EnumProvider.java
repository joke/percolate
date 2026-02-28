package io.github.joke.percolate.spi.impl;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.graph.edge.ConversionEdge;
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
    public boolean canHandle(TypeMirror source, TypeMirror target, ProcessingEnvironment env) {
        return canConvertEnums(source, target);
    }

    @Override
    public ConversionFragment provide(
            TypeMirror source, TypeMirror target, MethodRegistry registry, ProcessingEnvironment env) {
        return ConversionFragment.of();
    }

    public static boolean canConvertEnums(TypeMirror source, TypeMirror target) {
        return isEnumType(source) && isEnumType(target);
    }

    public static ConversionEdge createEnumEdge(TypeMirror source, TypeMirror target) {
        String targetName = ((DeclaredType) target).asElement().toString();
        return new ConversionEdge(
                ConversionEdge.Kind.ENUM_VALUE_OF, source, target, targetName + ".valueOf($expr.name())");
    }

    private static boolean isEnumType(TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return false;
        }
        return ((DeclaredType) type).asElement().getKind() == ElementKind.ENUM;
    }
}
