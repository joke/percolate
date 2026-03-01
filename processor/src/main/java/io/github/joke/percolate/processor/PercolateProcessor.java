package io.github.joke.percolate.processor;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.Mapper;
import io.github.joke.percolate.di.DaggerProcessorComponent;
import io.github.joke.percolate.di.ProcessorComponent;
import io.github.joke.percolate.di.ProcessorModule;
import java.util.Collections;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

@AutoService(Processor.class)
public class PercolateProcessor extends AbstractProcessor {

    private ProcessorComponent component;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        component = DaggerProcessorComponent.factory().create(new ProcessorModule(processingEnv));
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(Mapper.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) {
            return false;
        }
        component.roundComponentFactory().create().processor().process(annotations, roundEnv);
        return false;
    }
}
