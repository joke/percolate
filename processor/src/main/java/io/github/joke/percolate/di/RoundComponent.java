package io.github.joke.percolate.di;

import dagger.Subcomponent;
import io.github.joke.percolate.processor.RoundProcessor;

@RoundScoped
@Subcomponent(modules = RoundModule.class)
public interface RoundComponent {

    RoundProcessor processor();

    @Subcomponent.Factory
    interface Factory {
        RoundComponent create();
    }
}
