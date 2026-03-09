package io.github.joke.percolate.spi.impl;

import static java.util.stream.Collectors.toSet;
import static javax.lang.model.element.ElementKind.METHOD;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.model.Property;
import io.github.joke.percolate.spi.PropertyDiscoveryStrategy;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

@AutoService(PropertyDiscoveryStrategy.class)
public final class GetterPropertyStrategy implements PropertyDiscoveryStrategy {

    @Override
    public Set<Property> discoverProperties(final TypeElement type, final ProcessingEnvironment env) {
        return type.getEnclosedElements().stream()
                .filter(element -> element.getKind() == METHOD)
                .filter(element -> element.getModifiers().contains(PUBLIC))
                .map(element -> (ExecutableElement) element)
                .filter(element -> element.getParameters().isEmpty())
                .filter(GetterPropertyStrategy::isGetter)
                .map(GetterPropertyStrategy::toProperty)
                .collect(toSet());
    }

    private static boolean isGetter(final ExecutableElement method) {
        final var name = method.getSimpleName().toString();
        return (name.startsWith("get") && name.length() > 3) || (name.startsWith("is") && name.length() > 2);
    }

    private static Property toProperty(final ExecutableElement method) {
        final var name = method.getSimpleName().toString();
        final var propertyName =
                name.startsWith("get") ? decapitalize(name.substring(3)) : decapitalize(name.substring(2));
        return new Property(propertyName, method.getReturnType(), method);
    }

    private static String decapitalize(final String name) {
        if (name.isEmpty()) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
