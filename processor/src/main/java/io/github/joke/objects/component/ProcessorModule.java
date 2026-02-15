package io.github.joke.objects.component;

import dagger.Module;
import dagger.Provides;
import io.github.joke.objects.immutable.ImmutableSubcomponent;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;

@Module(subcomponents = ImmutableSubcomponent.class)
public class ProcessorModule {

    private final ProcessingEnvironment processingEnvironment;

    public ProcessorModule(ProcessingEnvironment processingEnvironment) {
        this.processingEnvironment = processingEnvironment;
    }

    @Provides
    Filer filer() {
        return processingEnvironment.getFiler();
    }

    @Provides
    Messager messager() {
        return processingEnvironment.getMessager();
    }
}
