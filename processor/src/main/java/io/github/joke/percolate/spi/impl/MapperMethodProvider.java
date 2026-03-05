package io.github.joke.percolate.spi.impl;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.graph.node.MethodCallNode;
import io.github.joke.percolate.model.MethodDefinition;
import io.github.joke.percolate.spi.ConversionFragment;
import io.github.joke.percolate.spi.ConversionProvider;
import io.github.joke.percolate.stage.MethodRegistry;
import io.github.joke.percolate.stage.RegistryEntry;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

@AutoService(ConversionProvider.class)
public final class MapperMethodProvider implements ConversionProvider {

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean canHandle(
            final TypeMirror source,
            final TypeMirror target,
            final MethodRegistry registry,
            final ProcessingEnvironment env) {
        return findMethod(env.getTypeUtils(), source, target, registry).isPresent();
    }

    @Override
    public ConversionFragment provide(
            final TypeMirror source,
            final TypeMirror target,
            final MethodRegistry registry,
            final ProcessingEnvironment env) {
        return findMethod(env.getTypeUtils(), source, target, registry)
                .map(method -> ConversionFragment.of(new MethodCallNode(method, source, target)))
                .orElse(ConversionFragment.of());
    }

    private static Optional<MethodDefinition> findMethod(
            final Types types,
            final TypeMirror source,
            final TypeMirror target,
            final MethodRegistry registry) {
        return registry.entries().values().stream()
                .map(RegistryEntry::getSignature)
                .filter(Objects::nonNull)
                .filter(method -> method.getParameters().size() == 1)
                .filter(method ->
                        types.isSameType(types.erasure(method.getParameters().get(0).getType()), types.erasure(source)))
                .filter(method -> types.isSameType(types.erasure(method.getReturnType()), types.erasure(target)))
                .findFirst();
    }
}
