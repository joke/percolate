package io.github.joke.percolate.processor;

import io.github.joke.percolate.processor.internal.stages.Stage;
import jakarta.inject.Inject;
import java.util.List;
import javax.lang.model.element.TypeElement;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;

@RequiredArgsConstructor(onConstructor_ = @Inject)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
final class Pipeline {

    private final List<Stage> stages;
    private final DiagnosticEmitter diagnosticEmitter;

    // Runs every stage for element. A stage throwing mid-pipeline would otherwise lose whatever diagnostics were
    // already collected on ctx (design D14) — the finally flushes them in that case only, leaving the normal, non-
    // throwing path's emit-or-defer decision to MapperStep.
    @VisibleForTesting
    MapperContext process(final TypeElement element) {
        final var ctx = new MapperContext(element);
        var completed = false;
        try {
            stages.forEach(s -> s.run(ctx));
            completed = true;
            return ctx;
        } finally {
            if (!completed) {
                diagnosticEmitter.flush(element, ctx.getDiagnostics());
            }
        }
    }
}
