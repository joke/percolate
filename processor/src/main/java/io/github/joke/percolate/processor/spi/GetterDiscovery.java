package io.github.joke.percolate.processor.spi;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.processor.model.GetterAccessor;
import io.github.joke.percolate.processor.model.ReadAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

@AutoService(SourcePropertyDiscovery.class)
public final class GetterDiscovery implements SourcePropertyDiscovery {

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public List<ReadAccessor> discover(final TypeMirror type, final Elements elements, final Types types) {
        if (!(type instanceof DeclaredType)) {
            return List.of();
        }

        final List<ReadAccessor> accessors = new ArrayList<>();

        final TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();
        for (final var enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.METHOD) {
                continue;
            }
            if (!enclosed.getModifiers().contains(Modifier.PUBLIC)) {
                continue;
            }
            final ExecutableElement method = (ExecutableElement) enclosed;
            if (!method.getParameters().isEmpty()) {
                continue;
            }

            final String methodName = method.getSimpleName().toString();
            final String propertyName = extractPropertyName(methodName);
            if (propertyName != null) {
                accessors.add(new GetterAccessor(propertyName, method.getReturnType(), method));
            }
        }

        return List.copyOf(accessors);
    }

    @SuppressWarnings("NullAway")
    private static @org.jspecify.annotations.Nullable String extractPropertyName(final String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3 && Character.isUpperCase(methodName.charAt(3))) {
            return methodName.substring(3, 4).toLowerCase(Locale.ROOT) + methodName.substring(4);
        }
        if (methodName.startsWith("is") && methodName.length() > 2 && Character.isUpperCase(methodName.charAt(2))) {
            return methodName.substring(2, 3).toLowerCase(Locale.ROOT) + methodName.substring(3);
        }
        return null;
    }
}
