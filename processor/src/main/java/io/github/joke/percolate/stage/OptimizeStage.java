package io.github.joke.percolate.stage;

import io.github.joke.percolate.di.RoundScoped;
import javax.inject.Inject;

@RoundScoped
public class OptimizeStage {

    @Inject
    OptimizeStage() {}

    public OptimizedGraphResult execute(ValidationResult validationResult) {
        return new OptimizedGraphResult(validationResult.graphResult());
    }
}
