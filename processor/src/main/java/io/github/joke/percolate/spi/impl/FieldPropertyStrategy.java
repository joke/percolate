package io.github.joke.percolate.spi.impl;

import static java.util.stream.Collectors.toSet;
import static javax.lang.model.element.ElementKind.FIELD;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PROTECTED;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.model.Property;
import io.github.joke.percolate.spi.PropertyDiscoveryStrategy;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

@AutoService(PropertyDiscoveryStrategy.class)
public final class FieldPropertyStrategy implements PropertyDiscoveryStrategy {

    @Override
    public Set<Property> discoverProperties(TypeElement type, ProcessingEnvironment env) {
        return type.getEnclosedElements().stream()
                .filter(e -> e.getKind() == FIELD)
                .filter(e -> !e.getModifiers().contains(PRIVATE))
                .filter(e -> !e.getModifiers().contains(PROTECTED))
                .map(e -> (VariableElement) e)
                .map(e -> new Property(e.getSimpleName().toString(), e.asType(), e))
                .collect(toSet());
    }
}
