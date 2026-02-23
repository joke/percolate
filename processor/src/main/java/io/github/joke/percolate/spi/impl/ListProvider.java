package io.github.joke.percolate.spi.impl;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import io.github.joke.percolate.graph.edge.ConversionEdge;
import io.github.joke.percolate.model.MapperDefinition;
import io.github.joke.percolate.spi.ConversionProvider;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import org.jspecify.annotations.Nullable;

public final class ListProvider implements ConversionProvider {

    private final List<MapperDefinition> mappers;

    public ListProvider(List<MapperDefinition> mappers) {
        this.mappers = mappers;
    }

    @Override
    public List<Conversion> possibleConversions(TypeMirror source, @Nullable ProcessingEnvironment env) {
        if (env == null || !isListType(source)) {
            return emptyList();
        }
        TypeMirror sourceElement = getFirstTypeArgument(source);
        if (sourceElement == null) {
            return emptyList();
        }
        Types types = env.getTypeUtils();
        TypeElement listElement = env.getElementUtils().getTypeElement("java.util.List");
        if (listElement == null) {
            return emptyList();
        }
        return mappers.stream()
                .flatMap(mapper -> mapper.getMethods().stream())
                .filter(m -> m.getParameters().size() == 1)
                .filter(m -> types.isSameType(
                        types.erasure(m.getParameters().get(0).getType()),
                        types.erasure(sourceElement)))
                .map(m -> {
                    TypeMirror targetElement = m.getReturnType();
                    DeclaredType targetListType = types.getDeclaredType(listElement, targetElement);
                    String template = "$expr.stream().map(this::" + m.getName()
                            + ").collect(java.util.stream.Collectors.toList())";
                    return new Conversion(targetListType,
                            new ConversionEdge(ConversionEdge.Kind.LIST_MAP, source, targetListType, template));
                })
                .collect(toList());
    }

    private static boolean isListType(TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return false;
        }
        return "java.util.List".equals(((DeclaredType) type).asElement().toString());
    }

    private static @Nullable TypeMirror getFirstTypeArgument(TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return null;
        }
        List<? extends TypeMirror> args = ((DeclaredType) type).getTypeArguments();
        return args.isEmpty() ? null : args.get(0);
    }
}
