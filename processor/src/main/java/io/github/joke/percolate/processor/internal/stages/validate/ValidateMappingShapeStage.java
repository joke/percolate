package io.github.joke.percolate.processor.internal.stages.validate;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.Diagnostic;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.internal.stages.Stage;
import io.github.joke.percolate.processor.model.MapperMappings;
import io.github.joke.percolate.processor.model.MappingDirective;
import io.github.joke.percolate.processor.model.MethodMappings;
import jakarta.inject.Inject;
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
 * <p>Each error is recorded as a permanent {@link Diagnostic} with the offending {@code AnnotationValue} for IDE
 * underlining (an annotation shape mistake cannot become valid on a later round); the stage never halts the
 * pipeline (it returns the surviving directives, and codegen is skipped once any error is recorded).
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class ValidateMappingShapeStage implements Stage {

    @Override
    public void run(final MapperContext ctx) {
        final var mappings = ctx.getMappings();
        if (mappings == null) {
            return;
        }
        ctx.setMappings(validate(mappings, ctx));
    }

    MapperMappings validate(final MapperMappings mappings, final MapperContext ctx) {
        final var methods = mappings.getMethods().stream()
                .map(method -> validateMethod(method, ctx))
                .collect(toUnmodifiableList());
        return new MapperMappings(mappings.getType(), methods);
    }

    MethodMappings validateMethod(final MethodMappings methodMappings, final MapperContext ctx) {
        final var kept = methodMappings.getDirectives().stream()
                .filter(directive -> validateDirective(directive, ctx))
                .collect(toUnmodifiableList());
        return new MethodMappings(methodMappings.getMethod(), kept);
    }

    /** Reports any shape errors and returns whether the directive is well-formed enough to keep seeding. */
    boolean validateDirective(final MappingDirective directive, final MapperContext ctx) {
        final var wellFormed = checkSourceXorConstant(directive, ctx);
        checkDefaultRequiresSource(directive, ctx);
        return wellFormed;
    }

    boolean checkSourceXorConstant(final MappingDirective directive, final MapperContext ctx) {
        return !reportsBothSourceAndConstant(directive, ctx) && !reportsNeitherSourceNorConstant(directive, ctx);
    }

    boolean reportsBothSourceAndConstant(final MappingDirective directive, final MapperContext ctx) {
        if (!(directive.hasSource() && directive.hasConstant())) {
            return false;
        }
        ctx.report(Diagnostic.error(
                        directive.getConstantSubject(),
                        "@Map declares both 'source' and 'constant'; they are mutually exclusive")
                .asPermanent());
        return true;
    }

    boolean reportsNeitherSourceNorConstant(final MappingDirective directive, final MapperContext ctx) {
        if (directive.hasSource() || directive.hasConstant()) {
            return false;
        }
        ctx.report(Diagnostic.error(directive.getTargetSubject(), "@Map must declare a 'source' or a 'constant'")
                .asPermanent());
        return true;
    }

    void checkDefaultRequiresSource(final MappingDirective directive, final MapperContext ctx) {
        if (directive.hasDefaultValue() && !directive.hasSource()) {
            ctx.report(Diagnostic.error(directive.getDefaultValueSubject(), "@Map 'defaultValue' requires a 'source'")
                    .asPermanent());
        }
    }
}
