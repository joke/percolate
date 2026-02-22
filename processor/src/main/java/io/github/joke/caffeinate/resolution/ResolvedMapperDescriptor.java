package io.github.joke.caffeinate.resolution;

import java.util.List;
import javax.lang.model.element.TypeElement;

public final class ResolvedMapperDescriptor {
    private final TypeElement mapperInterface;
    private final List<ResolvedMappingMethod> methods;

    public ResolvedMapperDescriptor(TypeElement mapperInterface, List<ResolvedMappingMethod> methods) {
        this.mapperInterface = mapperInterface;
        this.methods = List.copyOf(methods);
    }

    public TypeElement getMapperInterface() { return mapperInterface; }
    public List<ResolvedMappingMethod> getMethods() { return methods; }
}
