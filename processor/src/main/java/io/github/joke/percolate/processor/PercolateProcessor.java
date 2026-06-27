package io.github.joke.percolate.processor;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.service.AutoService;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@AutoService(Processor.class)
@NoArgsConstructor
public final class PercolateProcessor extends BasicAnnotationProcessor {

    private @Nullable ProcessorComponent component;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Set.of(
                ProcessorOptions.DEBUG_GRAPHS,
                ProcessorOptions.NULLABLE_ANNOTATIONS,
                ProcessorOptions.LOCALS_FINAL,
                ProcessorOptions.LOCALS_VAR,
                ProcessorOptions.DOC_TAGS);
    }

    @Override
    protected Iterable<? extends Step> steps() {
        if (component == null) {
            component = DaggerProcessorComponent.factory().create(new ProcessorModule(processingEnv));
        }
        return List.of(Objects.requireNonNull(component).mapperStep());
    }

    /**
     * On the final round, flush the recorded {@code no plan} diagnostics for any mapper still deferred.
     * {@code BasicAnnotationProcessor} does not invoke a {@code Step} at {@code processingOver}, so a
     * genuinely un-realisable mapper (no later round ever completed its types) is diagnosed here. This
     * is the only round-state the processor touches; the pipeline stages stay round-agnostic.
     */
    @Override
    protected void postRound(final RoundEnvironment roundEnv) {
        if (roundEnv.processingOver() && component != null) {
            component.mapperStep().flushDeferredDiagnostics();
        }
    }
}
