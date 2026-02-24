package io.github.joke.percolate.spi.impl;

import static java.util.Collections.emptyList;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.graph.edge.ConversionEdge;
import io.github.joke.percolate.spi.ConversionProvider;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

@AutoService(ConversionProvider.class)
public final class EnumProvider implements ConversionProvider {

    @Override
    public List<Conversion> possibleConversions(TypeMirror source, @Nullable ProcessingEnvironment env) {
        return emptyList();
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
