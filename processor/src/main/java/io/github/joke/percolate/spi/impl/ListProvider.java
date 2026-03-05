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
    public boolean canHandle(
            final TypeMirror source,
            final TypeMirror target,
            final MethodRegistry registry,
            final ProcessingEnvironment env) {
        return isListType(source) && isListType(target);
    }

    @Override
    public ConversionFragment provide(
            final TypeMirror source,
            final TypeMirror target,
            final MethodRegistry registry,
            final ProcessingEnvironment env) {
        final var sourceElement = getFirstTypeArgument(source);
        final var targetElement = getFirstTypeArgument(target);
        if (sourceElement == null || targetElement == null) {
            return ConversionFragment.of();
        }
        final var types = env.getTypeUtils();
        final var method = findMethod(types, sourceElement, targetElement, registry);
        if (method.isEmpty()) {
            return ConversionFragment.of();
        }
        return ConversionFragment.of(
                new CollectionIterationNode(source, sourceElement),
                new MethodCallNode(method.get(), sourceElement, targetElement),
                new CollectionCollectNode(target, targetElement));
    }

    private static Optional<MethodDefinition> findMethod(
            final Types types,
            final TypeMirror sourceElement,
            final TypeMirror targetElement,
            final MethodRegistry registry) {
        return registry.entries().values().stream()
                .map(RegistryEntry::getSignature)
                .filter(Objects::nonNull)
                .filter(method -> method.getParameters().size() == 1)
                .filter(method -> types.isSameType(
                        types.erasure(method.getParameters().get(0).getType()), types.erasure(sourceElement)))
                .filter(method -> types.isSameType(types.erasure(method.getReturnType()), types.erasure(targetElement)))
                .findFirst();
    }

    private static boolean isListType(final TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return false;
        }
        return "java.util.List".equals(((DeclaredType) type).asElement().toString());
    }

    private static @Nullable TypeMirror getFirstTypeArgument(final TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return null;
        }
        final var args = ((DeclaredType) type).getTypeArguments();
        return args.isEmpty() ? null : args.get(0);
    }
}
