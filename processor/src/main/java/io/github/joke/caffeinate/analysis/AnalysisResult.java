package io.github.joke.caffeinate.analysis;

import java.util.List;

public final class AnalysisResult {
    private final List<MapperDescriptor> mappers;

    public AnalysisResult(List<MapperDescriptor> mappers) {
        this.mappers = List.copyOf(mappers);
    }

    public List<MapperDescriptor> getMappers() {
        return mappers;
    }
}
