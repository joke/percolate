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
    public Set<Property> discoverProperties(TypeElement type, ProcessingEnvironment env) {
        return type.getEnclosedElements().stream()
                .filter(e -> e.getKind() == METHOD)
                .filter(e -> e.getModifiers().contains(PUBLIC))
                .map(e -> (ExecutableElement) e)
                .filter(e -> e.getParameters().isEmpty())
                .filter(e -> isGetter(e))
                .map(e -> toProperty(e))
                .collect(toSet());
    }

    private static boolean isGetter(ExecutableElement method) {
        String name = method.getSimpleName().toString();
        return (name.startsWith("get") && name.length() > 3) || (name.startsWith("is") && name.length() > 2);
    }

    private static Property toProperty(ExecutableElement method) {
        String name = method.getSimpleName().toString();
        String propertyName;
        if (name.startsWith("get")) {
            propertyName = decapitalize(name.substring(3));
        } else {
            propertyName = decapitalize(name.substring(2));
        }
        return new Property(propertyName, method.getReturnType(), method);
    }

    private static String decapitalize(String name) {
        if (name.isEmpty()) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
