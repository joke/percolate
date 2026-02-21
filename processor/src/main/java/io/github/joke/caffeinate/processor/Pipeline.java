package io.github.joke.caffeinate.processor;

import io.github.joke.caffeinate.analysis.AnalysisResult;
import io.github.joke.caffeinate.analysis.AnalysisStage;
import io.github.joke.caffeinate.codegen.CodeGenStage;
import io.github.joke.caffeinate.graph.GraphResult;
import io.github.joke.caffeinate.graph.GraphStage;
import io.github.joke.caffeinate.validation.ValidationResult;
import io.github.joke.caffeinate.validation.ValidationStage;

import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import java.util.Set;

public class Pipeline {

    private final AnalysisStage analysisStage;
    private final ValidationStage validationStage;
    private final GraphStage graphStage;
    private final CodeGenStage codeGenStage;
    private final RoundEnvironment roundEnv;

    @Inject
    public Pipeline(AnalysisStage analysisStage, ValidationStage validationStage,
                    GraphStage graphStage, CodeGenStage codeGenStage,
                    RoundEnvironment roundEnv) {
        this.analysisStage = analysisStage;
        this.validationStage = validationStage;
        this.graphStage = graphStage;
        this.codeGenStage = codeGenStage;
        this.roundEnv = roundEnv;
    }

    public void run(Set<? extends Element> mapperElements) {
        AnalysisResult analysis = analysisStage.analyze(roundEnv, mapperElements);
        ValidationResult validation = validationStage.validate(analysis);
        if (validation.hasFatalErrors()) return;
        GraphResult graph = graphStage.build(validation);
        codeGenStage.generate(graph, validation.getMappers());
    }
}
