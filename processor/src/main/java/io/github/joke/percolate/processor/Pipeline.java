package io.github.joke.percolate.processor;

import io.github.joke.percolate.di.RoundScoped;
import java.util.Set;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;

@RoundScoped
public class Pipeline {

    @Inject
    Pipeline() {}

    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // stages will be wired in here
    }
}
