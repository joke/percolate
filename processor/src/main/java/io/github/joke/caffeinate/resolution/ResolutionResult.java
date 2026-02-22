package io.github.joke.caffeinate.resolution;

import java.util.List;

public final class ResolutionResult {
    private final List<ResolvedMapperDescriptor> mappers;
    private final ConverterRegistry converterRegistry;

    public ResolutionResult(List<ResolvedMapperDescriptor> mappers, ConverterRegistry converterRegistry) {
        this.mappers = List.copyOf(mappers);
        this.converterRegistry = converterRegistry;
    }

    public List<ResolvedMapperDescriptor> getMappers() {
        return mappers;
    }

    public ConverterRegistry getConverterRegistry() {
        return converterRegistry;
    }
}
