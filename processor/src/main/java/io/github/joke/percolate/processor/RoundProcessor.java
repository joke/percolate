package io.github.joke.percolate.processor;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.stage.MapperDiscoveryStage;
import java.util.Set;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;

@RoundScoped
public class RoundProcessor {

    private final MapperDiscoveryStage discoveryStage;
    private final Pipeline pipeline;

    @Inject
    RoundProcessor(MapperDiscoveryStage discoveryStage, Pipeline pipeline) {
        this.discoveryStage = discoveryStage;
        this.pipeline = pipeline;
    }

    public void process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        discoveryStage.execute(annotations, roundEnv).forEach(pipeline::process);
    }
}
