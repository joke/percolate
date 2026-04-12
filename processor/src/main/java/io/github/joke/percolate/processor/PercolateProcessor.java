package io.github.joke.percolate.processor;

import com.google.auto.service.AutoService;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

@AutoService(Processor.class)
@SupportedAnnotationTypes("io.github.joke.percolate.Mapper")
@SupportedOptions({"percolate.debug.graphs", "percolate.debug.graphs.format"})
public class PercolateProcessor extends AbstractProcessor {

    private Pipeline pipeline;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(final javax.annotation.processing.ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        final ProcessorComponent component = DaggerProcessorComponent.builder()
                .processorModule(new ProcessorModule(processingEnv))
                .build();
        pipeline = component.pipeline();
    }

    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        annotations.stream()
                .flatMap(annotation -> roundEnv.getElementsAnnotatedWith(annotation).stream())
                .filter(TypeElement.class::isInstance)
                .map(TypeElement.class::cast)
                .forEach(pipeline::process);
        return false;
    }
}
