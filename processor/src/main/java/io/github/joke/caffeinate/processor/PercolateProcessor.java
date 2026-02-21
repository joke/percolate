package io.github.joke.caffeinate.processor;

import com.google.auto.service.AutoService;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

@AutoService(Processor.class)
@SupportedAnnotationTypes("io.github.joke.caffeinate.Mapper")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class PercolateProcessor extends AbstractProcessor {

    @SuppressWarnings("NullAway.Init")
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
        Set<Element> mapperElements = new HashSet<>();
        for (TypeElement annotation : annotations) {
            mapperElements.addAll(roundEnv.getElementsAnnotatedWith(annotation));
        }
        if (mapperElements.isEmpty()) return false;
        processorComponent.roundComponentFactory().create().pipeline().run(mapperElements);
        return false;
    }
}
