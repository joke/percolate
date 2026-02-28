package io.github.joke.percolate.stage;

import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;

import io.github.joke.percolate.model.MapperDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.TypeElement;

public final class ParseResult {

    private final List<MapperDefinition> mappers;
    private final Map<TypeElement, MethodRegistry> registries;

    public ParseResult(List<MapperDefinition> mappers, Map<TypeElement, MethodRegistry> registries) {
        this.mappers = unmodifiableList(new ArrayList<>(mappers));
        this.registries = unmodifiableMap(registries);
    }

    public List<MapperDefinition> getMappers() {
        return mappers;
    }

    public Map<TypeElement, MethodRegistry> getRegistries() {
        return registries;
    }
}
