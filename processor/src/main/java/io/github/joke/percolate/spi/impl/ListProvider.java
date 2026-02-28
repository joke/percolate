package io.github.joke.percolate.spi.impl;

import io.github.joke.percolate.graph.node.CollectionCollectNode;
import io.github.joke.percolate.graph.node.CollectionIterationNode;
import io.github.joke.percolate.graph.node.MethodCallNode;
import io.github.joke.percolate.model.MapperDefinition;
import io.github.joke.percolate.model.MethodDefinition;
import io.github.joke.percolate.spi.ConversionFragment;
import io.github.joke.percolate.spi.ConversionProvider;
import io.github.joke.percolate.stage.MethodRegistry;
import java.util.List;
import java.util.Optional;
import javax.annotation.processing.ProcessingEnvironment;
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
    public boolean canHandle(TypeMirror source, TypeMirror target, ProcessingEnvironment env) {
        return isListType(source) && isListType(target);
    }

    @Override
    public ConversionFragment provide(
            TypeMirror source, TypeMirror target, MethodRegistry registry, ProcessingEnvironment env) {
        @Nullable TypeMirror sourceElement = getFirstTypeArgument(source);
        @Nullable TypeMirror targetElement = getFirstTypeArgument(target);
        if (sourceElement == null || targetElement == null) {
            return ConversionFragment.of();
        }
        Types types = env.getTypeUtils();
        Optional<MethodDefinition> method = findMethod(types, sourceElement, targetElement);
        if (!method.isPresent()) {
            return ConversionFragment.of();
        }
        return ConversionFragment.of(
                new CollectionIterationNode(source, sourceElement),
                new MethodCallNode(method.get(), sourceElement, targetElement),
                new CollectionCollectNode(target, targetElement));
    }

    private Optional<MethodDefinition> findMethod(Types types, TypeMirror sourceElement, TypeMirror targetElement) {
        return mappers.stream()
                .flatMap(mapper -> mapper.getMethods().stream())
                .filter(m -> m.getParameters().size() == 1)
                .filter(m -> types.isSameType(
                        types.erasure(m.getParameters().get(0).getType()), types.erasure(sourceElement)))
                .filter(m -> types.isSameType(types.erasure(m.getReturnType()), types.erasure(targetElement)))
                .findFirst();
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
