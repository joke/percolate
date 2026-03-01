package io.github.joke.percolate.processor;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.stage.BindingStage;
import io.github.joke.percolate.stage.MethodRegistry;
import io.github.joke.percolate.stage.ParseResult;
import io.github.joke.percolate.stage.ParseStage;
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

    @Inject
    Pipeline(ParseStage parseStage, BindingStage bindingStage, WiringStage wiringStage) {
        this.parseStage = parseStage;
        this.bindingStage = bindingStage;
        this.wiringStage = wiringStage;
    }

    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        ParseResult parseResult = parseStage.execute(annotations, roundEnv);
        MethodRegistry registry = bindingStage.execute(parseResult);
        wiringStage.execute(registry);
        // ValidateStage, OptimizeStage, CodeGenStage reconnected in future redesign
    }
}
