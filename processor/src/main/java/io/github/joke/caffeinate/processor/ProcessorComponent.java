package io.github.joke.caffeinate.processor;

import dagger.Component;

@ProcessorScoped
@Component(modules = {ProcessorModule.class, StrategyModule.class, RoundComponentModule.class})
public interface ProcessorComponent {
    RoundComponent.Factory roundComponentFactory();
}
