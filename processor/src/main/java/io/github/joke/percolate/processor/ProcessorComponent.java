package io.github.joke.percolate.processor;

import dagger.Component;
import jakarta.inject.Singleton;

@Singleton
@Component(modules = ProcessorModule.class)
interface ProcessorComponent {

    MapperStep mapperStep();

    @Component.Factory
    interface Factory {
        ProcessorComponent create(ProcessorModule processorModule);
    }
}
