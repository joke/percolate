package io.github.joke.objects;

import com.google.auto.service.AutoService;
import io.github.joke.objects.component.DaggerProcessorComponent;
import io.github.joke.objects.component.ProcessorModule;
import io.github.joke.objects.immutable.ImmutableSubcomponent;

import java.io.IOException;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

@AutoService(Processor.class)
public class ObjectsProcessor extends AbstractProcessor {

    private ImmutableSubcomponent immutableSubcomponent;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        var component = DaggerProcessorComponent.builder()
                .processorModule(new ProcessorModule(processingEnv))
                .build();
        immutableSubcomponent = component.immutable().create();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(Immutable.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                if (element.getKind() != ElementKind.INTERFACE) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "@Immutable can only be applied to interfaces",
                            element
                    );
                    continue;
                }
                try {
                    immutableSubcomponent.generator().generate((TypeElement) element);
                } catch (IOException e) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "Failed to generate implementation: " + e.getMessage(),
                            element
                    );
                }
            }
        }
        return false;
    }
}
