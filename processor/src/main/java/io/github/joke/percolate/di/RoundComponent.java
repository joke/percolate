package io.github.joke.percolate.di;

import dagger.Subcomponent;
import io.github.joke.percolate.processor.Pipeline;
import io.github.joke.percolate.stage.ParseStage;

@RoundScoped
@Subcomponent(modules = RoundModule.class)
public interface RoundComponent {

    ParseStage parseStage();

    Pipeline pipeline();

    @Subcomponent.Factory
    interface Factory {
        RoundComponent create();
    }
}
