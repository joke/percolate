package io.github.joke.caffeinate.processor;

import com.google.auto.service.AutoService;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.Set;

@AutoService(Processor.class)
@SupportedAnnotationTypes("io.github.joke.caffeinate.Mapper")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class PercolateProcessor extends AbstractProcessor {

    private ProcessorComponent processorComponent;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        processorComponent = DaggerProcessorComponent.builder()
                .processorModule(new ProcessorModule(processingEnv))
                .build();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return false;
        if (annotations.isEmpty()) return false;
        Set<? extends Element> mapperElements = roundEnv.getElementsAnnotatedWith(
                annotations.iterator().next());
        if (mapperElements.isEmpty()) return false;
        processorComponent.roundComponentFactory()
                .create(roundEnv)
                .pipeline()
                .run(mapperElements);
        return false;
    }
}
