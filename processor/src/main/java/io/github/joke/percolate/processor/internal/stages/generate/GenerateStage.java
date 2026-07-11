package io.github.joke.percolate.processor.internal.stages.generate;

import io.github.joke.percolate.processor.Diagnostics;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.internal.stages.Stage;
import jakarta.inject.Inject;

@SuppressWarnings("PMD.AvoidCatchingGenericException")
@lombok.RequiredArgsConstructor(onConstructor_ = @Inject)
public final class GenerateStage implements Stage {

    private final Diagnostics diagnostics;
    private final BuildMethodBodies buildMethodBodies;
    private final AssembleMapperType assembleMapperType;

    @Override
    public void run(final MapperContext ctx) {
        // Skip a scarred mapper, and skip one whose realisation is unsatisfied (deferred for a later
        // round, or genuinely un-realisable) — its graph is incomplete, so there is nothing to emit.
        if (diagnostics.hasErrorsFor(ctx.getMapperType())
                || !ctx.getUnsatisfiedRealisation().isEmpty()) {
            return;
        }

        try {
            final var methodBodies = buildMethodBodies.build(ctx);
            assembleMapperType.assemble(ctx, methodBodies);
        } catch (final Throwable t) {
            diagnostics.error(ctx.getMapperType(), "code generation failed: " + t.getMessage());
        }
    }
}
