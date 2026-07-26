package io.github.joke.percolate.processor.internal.stages.validate;

import static java.util.stream.Collectors.toUnmodifiableSet;

import io.github.joke.percolate.processor.Diagnostic;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.internal.graph.MethodScope;
import io.github.joke.percolate.processor.internal.stages.Stage;
import io.github.joke.percolate.processor.model.EnumOverrideDirective;
import io.github.joke.percolate.spi.Subjects;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;

/**
 * Validates a method's {@code @MapEnum} table (already discovered onto its {@link
 * io.github.joke.percolate.processor.model.GoalSpec}, design D9) against the actual constants of the return type
 * (the {@code target}) and, for a single-parameter method, the parameter type (the {@code source}): a name that
 * does not match a real constant of the respective {@code enum} is reported as a compile error naming the unknown
 * constant. Purely structural — run before expansion, no graph needed. A method whose return type is not an
 * {@code enum}, or whose single parameter is not an {@code enum}, is silently skipped for the corresponding side —
 * {@code @MapEnum} declared on a non-enum-conversion method has no effect and is not this stage's concern.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class ValidateEnumOverridesStage implements Stage {

    @Override
    public void run(final MapperContext ctx) {
        final var shape = ctx.getShape();
        if (shape == null) {
            return;
        }
        shape.getAbstractMethods().forEach(method -> validateMethod(method, ctx));
    }

    void validateMethod(final ExecutableElement method, final MapperContext ctx) {
        final var goalSpec = ctx.getGoalSpecs().get(new MethodScope(method));
        if (goalSpec == null) {
            return;
        }
        final var overrides = goalSpec.getEnumOverrides();
        if (overrides.isEmpty()) {
            return;
        }
        enumConstantNames(method.getReturnType())
                .ifPresent(targetConstants -> overrides.forEach(o -> checkTarget(method, o, targetConstants, ctx)));
        singleParameterType(method)
                .flatMap(ValidateEnumOverridesStage::enumConstantNames)
                .ifPresent(sourceConstants -> overrides.forEach(o -> checkSource(method, o, sourceConstants, ctx)));
    }

    void checkTarget(
            final ExecutableElement method,
            final EnumOverrideDirective override,
            final Set<String> targetConstants,
            final MapperContext ctx) {
        if (!targetConstants.contains(override.getTarget())) {
            ctx.report(Diagnostic.error(
                            Subjects.of(method, override.getMirror(), override.getTargetValue()),
                            "@MapEnum names an unknown target constant '" + override.getTarget() + "'")
                    .asPermanent());
        }
    }

    void checkSource(
            final ExecutableElement method,
            final EnumOverrideDirective override,
            final Set<String> sourceConstants,
            final MapperContext ctx) {
        if (!sourceConstants.contains(override.getSource())) {
            ctx.report(Diagnostic.error(
                            Subjects.of(method, override.getMirror(), override.getSourceValue()),
                            "@MapEnum names an unknown source constant '" + override.getSource() + "'")
                    .asPermanent());
        }
    }

    static Optional<TypeMirror> singleParameterType(final ExecutableElement method) {
        return method.getParameters().size() == 1
                ? Optional.of(method.getParameters().get(0).asType())
                : Optional.empty();
    }

    static Optional<Set<String>> enumConstantNames(final TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return Optional.empty();
        }
        final var element = ((DeclaredType) type).asElement();
        if (element.getKind() != ElementKind.ENUM) {
            return Optional.empty();
        }
        return Optional.of(element.getEnclosedElements().stream()
                .filter(member -> member.getKind() == ElementKind.ENUM_CONSTANT)
                .map(member -> member.getSimpleName().toString())
                .collect(toUnmodifiableSet()));
    }
}
