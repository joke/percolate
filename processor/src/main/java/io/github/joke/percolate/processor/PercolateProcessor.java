package io.github.joke.percolate.processor;

import com.google.auto.common.BasicAnnotationProcessor;
import com.google.auto.common.BasicAnnotationProcessor.Step;
import com.google.auto.service.AutoService;
import java.util.List;
import java.util.Objects;
import javax.annotation.processing.Processor;
import javax.lang.model.SourceVersion;
import org.jspecify.annotations.Nullable;

@AutoService(Processor.class)
public final class PercolateProcessor extends BasicAnnotationProcessor {

    private @Nullable ProcessorComponent component;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    protected Iterable<? extends Step> steps() {
        if (component == null) {
            component = DaggerProcessorComponent.factory().create(new ProcessorModule(processingEnv));
        }
        return List.of(Objects.requireNonNull(component).mapperStep());
    }
}
