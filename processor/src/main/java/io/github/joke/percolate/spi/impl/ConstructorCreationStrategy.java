package io.github.joke.percolate.spi.impl;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.model.Property;
import io.github.joke.percolate.spi.CreationDescriptor;
import io.github.joke.percolate.spi.ObjectCreationStrategy;
import java.util.List;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

@AutoService(ObjectCreationStrategy.class)
public final class ConstructorCreationStrategy implements ObjectCreationStrategy {

    @Override
    public boolean canCreate(TypeElement type, ProcessingEnvironment env) {
        return type.getEnclosedElements().stream().anyMatch(e -> e.getKind() == CONSTRUCTOR);
    }

    @Override
    public CreationDescriptor describe(TypeElement type, ProcessingEnvironment env) {
        ExecutableElement constructor = type.getEnclosedElements().stream()
                .filter(e -> e.getKind() == CONSTRUCTOR)
                .map(e -> (ExecutableElement) e)
                .max(comparingInt(e -> e.getParameters().size()))
                .orElseThrow(() -> new IllegalStateException("No constructor found on " + type.getQualifiedName()));

        List<Property> parameters = constructor.getParameters().stream()
                .map(param -> new Property(param.getSimpleName().toString(), param.asType(), param))
                .collect(toList());

        return new CreationDescriptor(constructor, parameters);
    }
}
