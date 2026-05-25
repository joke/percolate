package io.github.joke.percolate.processor;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.service.AutoService;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.processing.Processor;
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
        return Set.of("percolate.debug.graphs", "percolate.nullable.annotations");
    }

    @Override
    protected Iterable<? extends Step> steps() {
        if (component == null) {
            component = DaggerProcessorComponent.factory().create(new ProcessorModule(processingEnv));
        }
        return List.of(Objects.requireNonNull(component).mapperStep());
    }
}
