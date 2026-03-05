package io.github.joke.percolate.spi.impl;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toUnmodifiableList;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.model.Property;
import io.github.joke.percolate.spi.CreationDescriptor;
import io.github.joke.percolate.spi.ObjectCreationStrategy;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

@AutoService(ObjectCreationStrategy.class)
public final class ConstructorCreationStrategy implements ObjectCreationStrategy {

    @Override
    public boolean canCreate(final TypeElement type, final ProcessingEnvironment env) {
        return type.getEnclosedElements().stream().anyMatch(element -> element.getKind() == CONSTRUCTOR);
    }

    @Override
    public CreationDescriptor describe(final TypeElement type, final ProcessingEnvironment env) {
        final var constructor = type.getEnclosedElements().stream()
                .filter(element -> element.getKind() == CONSTRUCTOR)
                .map(element -> (ExecutableElement) element)
                .max(comparingInt(element -> element.getParameters().size()))
                .orElseThrow(() -> new IllegalStateException("No constructor found on " + type.getQualifiedName()));

        final var parameters = constructor.getParameters().stream()
                .map(param -> new Property(param.getSimpleName().toString(), param.asType(), param))
                .collect(toUnmodifiableList());

        return new CreationDescriptor(constructor, parameters);
    }
}
