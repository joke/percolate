package io.github.joke.percolate.processor;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.stage.BindingStage;
import io.github.joke.percolate.stage.CodeGenStage;
import io.github.joke.percolate.stage.GraphBuildStage;
import io.github.joke.percolate.stage.GraphResult;
import io.github.joke.percolate.stage.MethodRegistry;
import io.github.joke.percolate.stage.OptimizeStage;
import io.github.joke.percolate.stage.OptimizedGraphResult;
import io.github.joke.percolate.stage.ParseResult;
import io.github.joke.percolate.stage.ParseStage;
import io.github.joke.percolate.stage.ResolveResult;
import io.github.joke.percolate.stage.ResolveStage;
import io.github.joke.percolate.stage.ValidateStage;
import io.github.joke.percolate.stage.ValidationResult;
import io.github.joke.percolate.stage.WiringStage;
import java.util.Set;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;

@RoundScoped
public class Pipeline {

    private final ParseStage parseStage;
    private final BindingStage bindingStage;
    private final WiringStage wiringStage;
    private final ResolveStage resolveStage;
    private final GraphBuildStage graphBuildStage;
    private final ValidateStage validateStage;
    private final OptimizeStage optimizeStage;
    private final CodeGenStage codeGenStage;

    @Inject
    Pipeline(
            ParseStage parseStage,
            BindingStage bindingStage,
            WiringStage wiringStage,
            ResolveStage resolveStage,
            GraphBuildStage graphBuildStage,
            ValidateStage validateStage,
            OptimizeStage optimizeStage,
            CodeGenStage codeGenStage) {
        this.parseStage = parseStage;
        this.bindingStage = bindingStage;
        this.wiringStage = wiringStage;
        this.resolveStage = resolveStage;
        this.graphBuildStage = graphBuildStage;
        this.validateStage = validateStage;
        this.optimizeStage = optimizeStage;
        this.codeGenStage = codeGenStage;
    }

    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        ParseResult parseResult = parseStage.execute(annotations, roundEnv);
        MethodRegistry registry = bindingStage.execute(parseResult);
        wiringStage.execute(registry);
        ResolveResult resolveResult = resolveStage.execute(parseResult);
        GraphResult graphResult = graphBuildStage.execute(resolveResult);
        ValidationResult validated = validateStage.execute(graphResult);
        if (validated.hasFatalErrors()) {
            return;
        }
        OptimizedGraphResult optimized = optimizeStage.execute(validated);
        codeGenStage.execute(optimized);
    }
}
