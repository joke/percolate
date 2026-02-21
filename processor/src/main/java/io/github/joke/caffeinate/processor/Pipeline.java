package io.github.joke.caffeinate.processor;

import io.github.joke.caffeinate.analysis.AnalysisResult;
import io.github.joke.caffeinate.analysis.AnalysisStage;
import io.github.joke.caffeinate.codegen.CodeGenStage;
import io.github.joke.caffeinate.graph.GraphStage;
import io.github.joke.caffeinate.validation.ValidationResult;
import io.github.joke.caffeinate.validation.ValidationStage;
import java.util.Set;
import javax.inject.Inject;
import javax.lang.model.element.Element;

public class Pipeline {

    private final AnalysisStage analysisStage;
    private final ValidationStage validationStage;
    private final GraphStage graphStage;
    private final CodeGenStage codeGenStage;

    @Inject
    public Pipeline(
            AnalysisStage analysisStage,
            ValidationStage validationStage,
            GraphStage graphStage,
            CodeGenStage codeGenStage) {
        this.analysisStage = analysisStage;
        this.validationStage = validationStage;
        this.graphStage = graphStage;
        this.codeGenStage = codeGenStage;
    }

    public void run(Set<? extends Element> mapperElements) {
        AnalysisResult analysis = analysisStage.analyze(mapperElements);
        ValidationResult validation = validationStage.validate(analysis);
        if (validation.hasFatalErrors()) return;
        graphStage.build(validation);
        codeGenStage.generate(validation.getMappers());
    }
}
