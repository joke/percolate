package io.github.joke.percolate.stage;

import static java.util.Collections.unmodifiableList;

import io.github.joke.percolate.model.MapperDefinition;
import java.util.ArrayList;
import java.util.List;

public final class ResolveResult {

    private final List<MapperDefinition> mappers;

    public ResolveResult(List<MapperDefinition> mappers) {
        this.mappers = unmodifiableList(new ArrayList<>(mappers));
    }

    public List<MapperDefinition> getMappers() {
        return mappers;
    }
}
