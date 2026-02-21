package io.github.joke.caffeinate.processor;

import dagger.Module;
import dagger.Provides;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

@Module
public class ProcessorModule {

    private final ProcessingEnvironment processingEnv;

    public ProcessorModule(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    @Provides
    @ProcessorScoped
    ProcessingEnvironment processingEnvironment() {
        return processingEnv;
    }

    @Provides
    @ProcessorScoped
    Messager messager() {
        return processingEnv.getMessager();
    }

    @Provides
    @ProcessorScoped
    Types types() {
        return processingEnv.getTypeUtils();
    }

    @Provides
    @ProcessorScoped
    Elements elements() {
        return processingEnv.getElementUtils();
    }

    @Provides
    @ProcessorScoped
    Filer filer() {
        return processingEnv.getFiler();
    }
}
