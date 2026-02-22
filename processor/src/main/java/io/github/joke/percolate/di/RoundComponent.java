package io.github.joke.percolate.di;

import dagger.Subcomponent;
import io.github.joke.percolate.processor.Pipeline;

@RoundScoped
@Subcomponent(modules = RoundModule.class)
public interface RoundComponent {

    Pipeline pipeline();

    @Subcomponent.Factory
    interface Factory {
        RoundComponent create();
    }
}
