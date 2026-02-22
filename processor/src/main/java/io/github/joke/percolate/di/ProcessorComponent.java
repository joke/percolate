package io.github.joke.percolate.di;

import dagger.Component;

@ProcessorScoped
@Component(modules = ProcessorModule.class)
public interface ProcessorComponent {

    RoundComponent.Factory roundComponentFactory();

    @Component.Factory
    interface Factory {
        ProcessorComponent create(ProcessorModule processorModule);
    }
}
