package io.github.joke.percolate.processor.internal.stages.discover;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.internal.graph.MethodScope;
import io.github.joke.percolate.processor.internal.stages.Stage;
import io.github.joke.percolate.processor.model.GoalSpec;
import io.github.joke.percolate.processor.model.MethodDirectives;
import io.github.joke.percolate.spi.DirectiveReader;
import jakarta.inject.Inject;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import lombok.RequiredArgsConstructor;

/**
 * Runs every {@link DirectiveReader} on the processor path against each abstract mapper method (design D7 of change
 * {@code decouple-engine-from-strategy-semantics}): the processor itself reads no mapping annotation — a reader
 * translates the annotations it owns into {@link DirectiveSinkImpl} calls, and this stage assembles what they
 * declared into the per-method {@link MethodDirectives} (consumed by {@link ValidateNoDuplicateTargetsStage} and
 * {@link io.github.joke.percolate.processor.internal.stages.validate.ValidateSourceParametersStage}) and the
 * per-method-scope {@link GoalSpec} the expansion driver reads.
 *
 * <p>What a reader {@code reject}s is reported here, verbatim and unconditionally — a malformed declaration is wrong
 * whether or not anything ever demands the path it names, so it cannot ride the candidate-refusal rail.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class DiscoverMappingsStage implements Stage {

    private final List<DirectiveReader> readers;

    @Override
    public void run(final MapperContext ctx) {
        final var shape = ctx.getShape();
        if (shape == null) {
            return;
        }
        final var methodDirectives = shape.getAbstractMethods().stream()
                .map(method -> readMethod(method, ctx))
                .collect(toUnmodifiableList());
        ctx.setMethodDirectives(methodDirectives);
        methodDirectives.forEach(directives -> ctx.getGoalSpecs()
                .put(
                        new MethodScope(directives.getMethod()),
                        GoalSpec.from(
                                directives.getBinds(),
                                directives.getInputsByTarget(),
                                directives.getConstraintsByTarget(),
                                directives.getScopeInputOverrides())));
    }

    MethodDirectives readMethod(final ExecutableElement method, final MapperContext ctx) {
        final var sink = new DirectiveSinkImpl();
        readers.forEach(reader -> reader.read(method, sink));
        sink.getRejections().forEach(ctx::report);
        return new MethodDirectives(
                method,
                sink.getBinds(),
                sink.getInputsByTarget(),
                sink.getScopeInputOverrides(),
                sink.getConstraintsByTarget());
    }
}
