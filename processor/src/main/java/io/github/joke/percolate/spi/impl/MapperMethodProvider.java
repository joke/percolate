package io.github.joke.percolate.spi.impl;

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

public final class MapperMethodProvider implements ConversionProvider {

    private final MethodRegistry registry;

    public MapperMethodProvider(MethodRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean canHandle(TypeMirror source, TypeMirror target, ProcessingEnvironment env) {
        return findMethod(env.getTypeUtils(), source, target).isPresent();
    }

    @Override
    public ConversionFragment provide(
            TypeMirror source, TypeMirror target, MethodRegistry ignored, ProcessingEnvironment env) {
        return findMethod(env.getTypeUtils(), source, target)
                .map(m -> ConversionFragment.of(new MethodCallNode(m, source, target)))
                .orElse(ConversionFragment.of());
    }

    private Optional<MethodDefinition> findMethod(Types types, TypeMirror source, TypeMirror target) {
        return registry.entries().values().stream()
                .map(RegistryEntry::getSignature)
                .filter(Objects::nonNull)
                .filter(m -> m.getParameters().size() == 1)
                .filter(m ->
                        types.isSameType(types.erasure(m.getParameters().get(0).getType()), types.erasure(source)))
                .filter(m -> types.isSameType(types.erasure(m.getReturnType()), types.erasure(target)))
                .findFirst();
    }
}
