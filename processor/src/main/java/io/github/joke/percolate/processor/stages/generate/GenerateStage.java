package io.github.joke.percolate.processor.stages.generate;

import io.github.joke.percolate.processor.Diagnostics;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.stages.Stage;
import jakarta.inject.Inject;

@SuppressWarnings("PMD.AvoidCatchingGenericException")
@lombok.RequiredArgsConstructor(onConstructor_ = @Inject)
public final class GenerateStage implements Stage {

    private final Diagnostics diagnostics;
    private final BuildMethodBodies buildMethodBodies;
    private final AssembleMapperType assembleMapperType;

    @Override
    public void run(final MapperContext ctx) {
        if (diagnostics.hasErrorsFor(ctx.getMapperType())) {
            return;
        }

        try {
            final var bodies = buildMethodBodies.build(ctx);
            assembleMapperType.assemble(ctx, bodies);
        } catch (final Throwable t) {
            diagnostics.error(ctx.getMapperType(), "code generation failed: " + t.getMessage());
        }
    }
}
