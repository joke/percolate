package io.github.joke.percolate.processor;

import com.palantir.javapoet.JavaFile;
import io.github.joke.percolate.processor.stage.AnalyzeStage;
import io.github.joke.percolate.processor.stage.BuildGraphStage;
import io.github.joke.percolate.processor.stage.DiscoverStage;
import io.github.joke.percolate.processor.stage.GenerateStage;
import io.github.joke.percolate.processor.stage.ValidateStage;
import jakarta.inject.Inject;
import javax.annotation.processing.Messager;
import javax.lang.model.element.TypeElement;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor(onConstructor_ = @Inject)
final class Pipeline {

    private final AnalyzeStage analyzeStage;
    private final DiscoverStage discoverStage;
    private final BuildGraphStage buildGraphStage;
    private final ValidateStage validateStage;
    private final GenerateStage generateStage;
    private final Messager messager;

    @Nullable
    JavaFile process(final TypeElement element) {
        final var analyzeResult = analyzeStage.execute(element);
        if (!analyzeResult.isSuccess()) {
            reportErrors(analyzeResult);
            return null;
        }

        final var discoverResult = discoverStage.execute(analyzeResult.value());
        if (!discoverResult.isSuccess()) {
            reportErrors(discoverResult);
            return null;
        }

        final var graphResult = buildGraphStage.execute(discoverResult.value());
        if (!graphResult.isSuccess()) {
            reportErrors(graphResult);
            return null;
        }

        final var validateResult = validateStage.execute(graphResult.value());
        if (!validateResult.isSuccess()) {
            reportErrors(validateResult);
            return null;
        }

        final var generateResult = generateStage.execute(validateResult.value());
        if (!generateResult.isSuccess()) {
            reportErrors(generateResult);
            return null;
        }

        return generateResult.value();
    }

    private void reportErrors(final StageResult<?> result) {
        for (final Diagnostic diagnostic : result.errors()) {
            messager.printMessage(diagnostic.getKind(), diagnostic.getMessage(), diagnostic.getElement());
        }
    }
}
