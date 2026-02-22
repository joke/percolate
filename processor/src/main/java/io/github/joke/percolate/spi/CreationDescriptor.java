package io.github.joke.percolate.spi;

import static java.util.Collections.unmodifiableList;

import io.github.joke.percolate.model.Property;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.ExecutableElement;

public final class CreationDescriptor {

    private final ExecutableElement constructor;
    private final List<Property> parameters;

    public CreationDescriptor(ExecutableElement constructor, List<Property> parameters) {
        this.constructor = constructor;
        this.parameters = unmodifiableList(new ArrayList<>(parameters));
    }

    public ExecutableElement getConstructor() {
        return constructor;
    }

    public List<Property> getParameters() {
        return parameters;
    }
}
