package io.github.joke.percolate.processor.spi;

import static java.util.stream.Collectors.toUnmodifiableList;
import static javax.lang.model.element.ElementKind.METHOD;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.processor.model.GetterAccessor;
import io.github.joke.percolate.processor.model.ReadAccessor;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.lang.model.element.ExecutableElement;
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

        final TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();
        return typeElement.getEnclosedElements().stream()
                .filter(e -> e.getKind() == METHOD)
                .filter(e -> e.getModifiers().contains(PUBLIC))
                .map(ExecutableElement.class::cast)
                .filter(m -> m.getParameters().isEmpty())
                .map(m -> {
                    final String propertyName =
                            extractPropertyName(m.getSimpleName().toString());
                    return propertyName != null
                            ? (ReadAccessor) new GetterAccessor(propertyName, m.getReturnType(), m)
                            : null;
                })
                .filter(Objects::nonNull)
                .collect(toUnmodifiableList());
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
