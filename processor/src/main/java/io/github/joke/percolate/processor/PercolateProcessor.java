package io.github.joke.percolate.processor;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.service.AutoService;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

import static io.github.joke.percolate.processor.DaggerProcessorComponent.factory;
import static io.github.joke.percolate.processor.ProcessorOptions.CLASSES_FINAL;
import static io.github.joke.percolate.processor.ProcessorOptions.CONSTRUCTION_PREFERENCE;
import static io.github.joke.percolate.processor.ProcessorOptions.DEBUG_GRAPHS;
import static io.github.joke.percolate.processor.ProcessorOptions.DOC_TAGS;
import static io.github.joke.percolate.processor.ProcessorOptions.LOCALS_FINAL;
import static io.github.joke.percolate.processor.ProcessorOptions.LOCALS_VAR;
import static io.github.joke.percolate.processor.ProcessorOptions.METHODS_FINAL;
import static io.github.joke.percolate.processor.ProcessorOptions.NULLABLE_ANNOTATIONS;
import static io.github.joke.percolate.processor.ProcessorOptions.PARAMETERS_FINAL;
import static io.github.joke.percolate.processor.ProcessorOptions.SWITCH_STYLE;
import static io.github.joke.percolate.processor.ProcessorOptions.TIME_ZONE;
import static java.util.Objects.requireNonNull;
import static javax.lang.model.SourceVersion.latestSupported;

@AutoService(Processor.class)
@NoArgsConstructor
public final class PercolateProcessor extends BasicAnnotationProcessor {

    private @Nullable ProcessorComponent component;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return latestSupported();
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Set.of(
                DEBUG_GRAPHS,
                NULLABLE_ANNOTATIONS,
                LOCALS_FINAL,
                LOCALS_VAR,
                PARAMETERS_FINAL,
                METHODS_FINAL,
                CLASSES_FINAL,
                DOC_TAGS,
                TIME_ZONE,
                SWITCH_STYLE,
                CONSTRUCTION_PREFERENCE);
    }

    @Override
    @VisibleForTesting
    protected Iterable<? extends Step> steps() {
        if (component == null) {
            component = factory().create(new ProcessorModule(processingEnv));
        }
        return List.of(requireNonNull(component).mapperStep());
    }

    // On the final round, flush the recorded no plan diagnostics for any mapper still deferred.
    // BasicAnnotationProcessor does not invoke a Step at processingOver, so a genuinely un-realisable mapper (no
    // later round ever completed its types) is diagnosed here. This is the only round-state the processor touches;
    // the pipeline stages stay round-agnostic.
    @Override
    @VisibleForTesting
    protected void postRound(final RoundEnvironment roundEnv) {
        if (roundEnv.processingOver() && component != null) {
            component.mapperStep().flushDeferredDiagnostics();
        }
    }
}
