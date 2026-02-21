package io.github.joke.caffeinate.processor;

import dagger.Subcomponent;
import io.github.joke.caffeinate.analysis.AnalysisStage;
import io.github.joke.caffeinate.codegen.CodeGenStage;
import io.github.joke.caffeinate.graph.GraphStage;
import io.github.joke.caffeinate.validation.ValidationStage;

@RoundScoped
@Subcomponent
public interface RoundComponent {

    AnalysisStage analysisStage();

    ValidationStage validationStage();

    GraphStage graphStage();

    CodeGenStage codeGenStage();

    Pipeline pipeline();

    @Subcomponent.Factory
    interface Factory {
        RoundComponent create();
    }
}
