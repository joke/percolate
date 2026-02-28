package io.github.joke.percolate.spi.impl;

import io.github.joke.percolate.graph.node.MethodCallNode;
import io.github.joke.percolate.model.MapperDefinition;
import io.github.joke.percolate.model.MethodDefinition;
import io.github.joke.percolate.spi.ConversionFragment;
import io.github.joke.percolate.spi.ConversionProvider;
import io.github.joke.percolate.stage.MethodRegistry;
import java.util.List;
import java.util.Optional;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

public final class MapperMethodProvider implements ConversionProvider {

    private final List<MapperDefinition> mappers;

    public MapperMethodProvider(List<MapperDefinition> mappers) {
        this.mappers = mappers;
    }

    @Override
    public boolean canHandle(TypeMirror source, TypeMirror target, ProcessingEnvironment env) {
        Types types = env.getTypeUtils();
        return findMethod(types, source, target).isPresent();
    }

    @Override
    public ConversionFragment provide(
            TypeMirror source, TypeMirror target, MethodRegistry registry, ProcessingEnvironment env) {
        Types types = env.getTypeUtils();
        Optional<MethodDefinition> method = findMethod(types, source, target);
        return method.map(m -> ConversionFragment.of(new MethodCallNode(m, source, target)))
                .orElse(ConversionFragment.of());
    }

    private Optional<MethodDefinition> findMethod(Types types, TypeMirror source, TypeMirror target) {
        return mappers.stream()
                .flatMap(mapper -> mapper.getMethods().stream())
                .filter(m -> m.getParameters().size() == 1)
                .filter(m ->
                        types.isSameType(types.erasure(m.getParameters().get(0).getType()), types.erasure(source)))
                .filter(m -> types.isSameType(types.erasure(m.getReturnType()), types.erasure(target)))
                .findFirst();
    }
}
