package io.github.joke.percolate.spi.impl;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import io.github.joke.percolate.graph.edge.ConversionEdge;
import io.github.joke.percolate.model.MapperDefinition;
import io.github.joke.percolate.spi.ConversionProvider;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import org.jspecify.annotations.Nullable;

public final class MapperMethodProvider implements ConversionProvider {

    private final List<MapperDefinition> mappers;

    public MapperMethodProvider(List<MapperDefinition> mappers) {
        this.mappers = mappers;
    }

    @Override
    public List<Conversion> possibleConversions(TypeMirror source, @Nullable ProcessingEnvironment env) {
        if (env == null) {
            return emptyList();
        }
        Types types = env.getTypeUtils();
        return mappers.stream()
                .flatMap(mapper -> mapper.getMethods().stream())
                .filter(m -> m.getParameters().size() == 1)
                .filter(m ->
                        types.isSameType(types.erasure(m.getParameters().get(0).getType()), types.erasure(source)))
                .map(m -> new Conversion(
                        m.getReturnType(),
                        new ConversionEdge(
                                ConversionEdge.Kind.MAPPER_METHOD,
                                source,
                                m.getReturnType(),
                                "this." + m.getName() + "($expr)")))
                .collect(toList());
    }
}
