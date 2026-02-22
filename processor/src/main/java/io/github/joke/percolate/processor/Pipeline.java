package io.github.joke.percolate.processor;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.stage.GraphBuildStage;
import io.github.joke.percolate.stage.GraphResult;
import io.github.joke.percolate.stage.ParseResult;
import io.github.joke.percolate.stage.ParseStage;
import io.github.joke.percolate.stage.ResolveResult;
import io.github.joke.percolate.stage.ResolveStage;
import io.github.joke.percolate.stage.ValidateStage;
import io.github.joke.percolate.stage.ValidationResult;
import java.util.Set;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;

@RoundScoped
public class Pipeline {

    private final ParseStage parseStage;
    private final ResolveStage resolveStage;
    private final GraphBuildStage graphBuildStage;
    private final ValidateStage validateStage;

    @Inject
    Pipeline(
            ParseStage parseStage,
            ResolveStage resolveStage,
            GraphBuildStage graphBuildStage,
            ValidateStage validateStage) {
        this.parseStage = parseStage;
        this.resolveStage = resolveStage;
        this.graphBuildStage = graphBuildStage;
        this.validateStage = validateStage;
    }

    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        ParseResult parseResult = parseStage.execute(annotations, roundEnv);
        ResolveResult resolveResult = resolveStage.execute(parseResult);
        GraphResult graphResult = graphBuildStage.execute(resolveResult);
        ValidationResult validated = validateStage.execute(graphResult);
        if (validated.hasFatalErrors()) {
            return;
        }
        // more stages will follow
    }
}
