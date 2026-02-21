package io.github.joke.caffeinate.analysis;

import java.util.List;
import javax.lang.model.element.TypeElement;

public final class MapperDescriptor {
    private final TypeElement mapperInterface;
    private final List<MappingMethod> methods;

    public MapperDescriptor(TypeElement mapperInterface, List<MappingMethod> methods) {
        this.mapperInterface = mapperInterface;
        this.methods = List.copyOf(methods);
    }

    public TypeElement getMapperInterface() {
        return mapperInterface;
    }

    public List<MappingMethod> getMethods() {
        return methods;
    }
}
