package io.github.joke.percolate.processor.stages.validate;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.Diagnostics;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.model.MapperMappings;
import io.github.joke.percolate.processor.model.MappingDirective;
import io.github.joke.percolate.processor.model.MethodMappings;
import io.github.joke.percolate.processor.stages.Stage;
import jakarta.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import lombok.RequiredArgsConstructor;

/**
 * Purely structural {@code @Map} shape validation, run before the seed stage (no graph needed):
 *
 * <ul>
 *   <li><strong>constant XOR source</strong> — exactly one of {@code source}/{@code constant} must be present. A
 *       directive declaring both is contradictory and one declaring neither has nothing to map; either is diagnosed
 *       and <em>dropped</em> so the seed stage only ever sees a clean source-or-constant directive.</li>
 *   <li><strong>{@code defaultValue} requires {@code source}</strong> — a fallback is meaningful only for an absent
 *       source value, so a present {@code defaultValue} without a present {@code source} (including on a
 *       {@code constant} directive) is diagnosed.</li>
 * </ul>
 *
 * <p>Each error is emitted via {@link Diagnostics} with the offending {@code AnnotationValue} for IDE underlining;
 * the stage never halts the pipeline (it returns the surviving directives, and codegen is skipped by the scarred
 * check once any error is recorded).
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class ValidateMappingShapeStage implements Stage {

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
        final var methods =
                mappings.getMethods().stream().map(this::validateMethod).collect(toUnmodifiableList());
        return new MapperMappings(mappings.getType(), methods);
    }

    private MethodMappings validateMethod(final MethodMappings methodMappings) {
        final var method = methodMappings.getMethod();
        final var kept = methodMappings.getDirectives().stream()
                .filter(directive -> validateDirective(directive, method))
                .collect(toUnmodifiableList());
        return new MethodMappings(method, kept);
    }

    /** Emits any shape errors and returns whether the directive is well-formed enough to keep seeding. */
    private boolean validateDirective(final MappingDirective directive, final ExecutableElement method) {
        final var wellFormed = checkSourceXorConstant(directive, method);
        checkDefaultRequiresSource(directive, method);
        return wellFormed;
    }

    private boolean checkSourceXorConstant(final MappingDirective directive, final ExecutableElement method) {
        if (directive.hasSource() && directive.hasConstant()) {
            diagnostics.error(
                    method,
                    directive.getMirror(),
                    directive.getConstantValue(),
                    "@Map declares both 'source' and 'constant'; they are mutually exclusive");
            return false;
        }
        if (!directive.hasSource() && !directive.hasConstant()) {
            diagnostics.error(
                    method,
                    directive.getMirror(),
                    directive.getTargetValue(),
                    "@Map must declare a 'source' or a 'constant'");
            return false;
        }
        return true;
    }

    private void checkDefaultRequiresSource(final MappingDirective directive, final ExecutableElement method) {
        if (directive.hasDefaultValue() && !directive.hasSource()) {
            diagnostics.error(
                    method,
                    directive.getMirror(),
                    directive.getDefaultValueValue(),
                    "@Map 'defaultValue' requires a 'source'");
        }
    }
}
