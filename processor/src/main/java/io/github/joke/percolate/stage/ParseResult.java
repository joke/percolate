package io.github.joke.percolate.stage;

import static java.util.Collections.unmodifiableList;

import io.github.joke.percolate.model.MapperDefinition;
import java.util.ArrayList;
import java.util.List;

public final class ParseResult {

    private final List<MapperDefinition> mappers;

    public ParseResult(List<MapperDefinition> mappers) {
        this.mappers = unmodifiableList(new ArrayList<>(mappers));
    }

    public List<MapperDefinition> getMappers() {
        return mappers;
    }
}
