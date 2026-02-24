package io.github.joke.percolate.spi.impl;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.graph.edge.ConversionEdge;
import io.github.joke.percolate.spi.ConversionProvider;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

@AutoService(ConversionProvider.class)
public final class OptionalProvider implements ConversionProvider {

    @Override
    public List<Conversion> possibleConversions(TypeMirror source, @Nullable ProcessingEnvironment env) {
        if (env == null) {
            return emptyList();
        }
        if (isOptionalType(source)) {
            List<? extends TypeMirror> args = ((DeclaredType) source).getTypeArguments();
            if (!args.isEmpty()) {
                TypeMirror inner = args.get(0);
                return singletonList(new Conversion(
                        inner,
                        new ConversionEdge(ConversionEdge.Kind.OPTIONAL_UNWRAP, source, inner, "$expr.orElse(null)")));
            }
        } else {
            TypeElement optionalElement = env.getElementUtils().getTypeElement("java.util.Optional");
            if (optionalElement != null) {
                DeclaredType optionalType = env.getTypeUtils().getDeclaredType(optionalElement, source);
                return singletonList(new Conversion(
                        optionalType,
                        new ConversionEdge(
                                ConversionEdge.Kind.OPTIONAL_WRAP,
                                source,
                                optionalType,
                                "java.util.Optional.ofNullable($expr)")));
            }
        }
        return emptyList();
    }

    private static boolean isOptionalType(TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return false;
        }
        return "java.util.Optional".equals(((DeclaredType) type).asElement().toString());
    }
}
