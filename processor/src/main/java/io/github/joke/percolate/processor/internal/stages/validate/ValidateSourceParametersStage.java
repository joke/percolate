package io.github.joke.percolate.processor.internal.stages.validate;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableSet;

import io.github.joke.percolate.processor.Diagnostics;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.internal.stages.Stage;
import io.github.joke.percolate.processor.model.MapperMappings;
import io.github.joke.percolate.processor.model.MappingDirective;
import io.github.joke.percolate.processor.model.MethodMappings;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import lombok.RequiredArgsConstructor;

/**
 * Hard precondition for the seed stage: every directive that survives this stage has a non-empty source whose
 * first segment names a method parameter. A directive that fails the check is diagnosed <em>and dropped</em> from
 * the mappings, so the seed stage never has to mint an orphan source node or silently skip an empty source.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class ValidateSourceParametersStage implements Stage {

    private final Diagnostics diagnostics;

    @Override
    public void run(final MapperContext ctx) {
        final var mappings = ctx.getMappings();
        if (mappings == null) {
            return;
        }
        ctx.setMappings(validate(mappings));
    }

    MapperMappings validate(final MapperMappings mappings) {
        final var validated =
                mappings.getMethods().stream().map(this::validateMethod).collect(toUnmodifiableList());
        return new MapperMappings(mappings.getType(), validated);
    }

    private MethodMappings validateMethod(final MethodMappings methodMappings) {
        final var method = methodMappings.getMethod();
        final var paramNames = method.getParameters().stream()
                .map(p -> p.getSimpleName().toString())
                .collect(toUnmodifiableSet());
        final var methodSig = formatMethodSig(method);

        final var valid = methodMappings.getDirectives().stream()
                .filter(d -> isValidOrDiagnose(d, paramNames, method, methodSig))
                .collect(toUnmodifiableList());
        return new MethodMappings(method, valid);
    }

    private boolean isValidOrDiagnose(
            final MappingDirective directive,
            final Set<String> paramNames,
            final ExecutableElement method,
            final String methodSig) {
        final var source = directive.getSource();
        if (source == null) {
            // A constant directive (or any sourceless directive) has no source to validate against a parameter.
            return true;
        }
        final var seg = firstSegment(source);
        if (paramNames.contains(seg)) {
            return true;
        }
        diagnostics.error(
                method,
                directive.getMirror(),
                directive.getSourceValue(),
                "unknown source parameter '" + seg + "' in @Map on " + methodSig);
        return false;
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
        return declaredSimpleName(mirror).orElseGet(mirror::toString);
    }

    private static Optional<String> declaredSimpleName(final javax.lang.model.type.TypeMirror mirror) {
        if (!(mirror instanceof javax.lang.model.type.DeclaredType)) {
            return Optional.empty();
        }
        final var elem = ((javax.lang.model.type.DeclaredType) mirror).asElement();
        return elem instanceof TypeElement ? Optional.of(elem.getSimpleName().toString()) : Optional.empty();
    }
}
