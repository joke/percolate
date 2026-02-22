package io.github.joke.percolate.processor;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.stage.ParseResult;
import io.github.joke.percolate.stage.ParseStage;
import io.github.joke.percolate.stage.ResolveStage;
import java.util.Set;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;

@RoundScoped
public class Pipeline {

    private final ParseStage parseStage;
    private final ResolveStage resolveStage;

    @Inject
    Pipeline(ParseStage parseStage, ResolveStage resolveStage) {
        this.parseStage = parseStage;
        this.resolveStage = resolveStage;
    }

    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        ParseResult parseResult = parseStage.execute(annotations, roundEnv);
        resolveStage.execute(parseResult);
        // more stages will follow
    }
}
