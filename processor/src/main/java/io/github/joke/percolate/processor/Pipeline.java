package io.github.joke.percolate.processor;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.stage.ParseStage;
import java.util.Set;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;

@RoundScoped
public class Pipeline {

    private final ParseStage parseStage;

    @Inject
    Pipeline(ParseStage parseStage) {
        this.parseStage = parseStage;
    }

    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        parseStage.execute(annotations, roundEnv);
        // more stages will follow
    }
}
