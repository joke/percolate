package io.github.joke.percolate.processor.internal.stages.generate;

import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.internal.stages.Stage;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import static io.github.joke.percolate.processor.Diagnostic.error;
import static io.github.joke.percolate.spi.Subjects.none;

@SuppressWarnings("PMD.AvoidCatchingGenericException")
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class GenerateStage implements Stage {

    private final BuildMethodBodies buildMethodBodies;
    private final AssembleMapperType assembleMapperType;

    @Override
    public void run(final MapperContext ctx) {
        // Skip a mapper that already has an error — whether scarred by an earlier stage, or unrealised
        // (recorded transient by RealisationDiagnosticsStage, deferred for a later round, or genuinely
        // un-realisable) — its graph is incomplete, so there is nothing to emit.
        if (ctx.hasErrors()) {
            return;
        }

        try {
            final var methodBodies = buildMethodBodies.build(ctx);
            assembleMapperType.assemble(ctx, methodBodies);
        } catch (final Throwable t) {
            ctx.report(
                    error(none(), "code generation failed: " + t.getMessage()).asPermanent());
        }
    }
}
