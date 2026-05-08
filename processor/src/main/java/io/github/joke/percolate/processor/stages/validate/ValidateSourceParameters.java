package io.github.joke.percolate.processor.stages.validate;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toUnmodifiableSet;

import io.github.joke.percolate.processor.Diagnostics;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.model.MapperMappings;
import io.github.joke.percolate.processor.model.MethodMappings;
import io.github.joke.percolate.processor.stages.Stage;
import jakarta.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class ValidateSourceParameters implements Stage {

    private final Diagnostics diagnostics;

    @Override
    public void run(MapperContext ctx) {
        final var mappings = ctx.getMappings();
        if (mappings == null) {
            return;
        }
        validate(mappings);
    }

    void validate(final MapperMappings mappings) {
        for (final var methodMappings : mappings.getMethods()) {
            validateMethod(methodMappings);
        }
    }

    private void validateMethod(final MethodMappings methodMappings) {
        final var method = methodMappings.getMethod();
        final var paramNames = method.getParameters().stream()
                .map(p -> p.getSimpleName().toString())
                .collect(toUnmodifiableSet());
        final var methodSig = formatMethodSig(method);

        for (final var directive : methodMappings.getDirectives()) {
            final var firstSegment = firstSegment(directive.getSource());
            if (!paramNames.contains(firstSegment)) {
                diagnostics.error(
                        method,
                        directive.getMirror(),
                        directive.getSourceValue(),
                        "unknown source parameter '" + firstSegment + "' in @Map on " + methodSig);
            }
        }
    }

    private static String firstSegment(final String source) {
        final var dot = source.indexOf('.');
        if (dot < 0) {
            return source;
        }
        return source.substring(0, dot);
    }

    private static String formatMethodSig(final ExecutableElement method) {
        final var name = method.getSimpleName().toString();
        final var paramTypes = method.getParameters().stream()
                .map(p -> simpleTypeName(p.asType()))
                .collect(joining(","));
        return name + "(" + paramTypes + ")";
    }

    private static String simpleTypeName(final javax.lang.model.type.TypeMirror mirror) {
        if (mirror == null) {
            return "?";
        }
        if (mirror.getKind() == TypeKind.TYPEVAR) {
            return mirror.toString();
        }
        if (mirror instanceof javax.lang.model.type.DeclaredType) {
            final var elem = ((javax.lang.model.type.DeclaredType) mirror).asElement();
            if (elem instanceof TypeElement) {
                return ((TypeElement) elem).getSimpleName().toString();
            }
        }
        return mirror.toString();
    }
}
