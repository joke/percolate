package io.github.joke.percolate.spi.impl;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.graph.node.CollectionCollectNode;
import io.github.joke.percolate.graph.node.CollectionIterationNode;
import io.github.joke.percolate.graph.node.MethodCallNode;
import io.github.joke.percolate.model.MethodDefinition;
import io.github.joke.percolate.spi.ConversionFragment;
import io.github.joke.percolate.spi.ConversionProvider;
import io.github.joke.percolate.stage.MethodRegistry;
import io.github.joke.percolate.stage.RegistryEntry;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import org.jspecify.annotations.Nullable;

@AutoService(ConversionProvider.class)
public final class ListProvider implements ConversionProvider {

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public boolean canHandle(TypeMirror source, TypeMirror target, MethodRegistry registry, ProcessingEnvironment env) {
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
        Optional<MethodDefinition> method = findMethod(types, sourceElement, targetElement, registry);
        if (!method.isPresent()) {
            return ConversionFragment.of();
        }
        return ConversionFragment.of(
                new CollectionIterationNode(source, sourceElement),
                new MethodCallNode(method.get(), sourceElement, targetElement),
                new CollectionCollectNode(target, targetElement));
    }

    private static Optional<MethodDefinition> findMethod(
            Types types, TypeMirror sourceElement, TypeMirror targetElement, MethodRegistry registry) {
        return registry.entries().values().stream()
                .map(RegistryEntry::getSignature)
                .filter(Objects::nonNull)
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
