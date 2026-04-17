package io.github.joke.percolate.processor;

import com.palantir.javapoet.JavaFile;
import io.github.joke.percolate.processor.stage.AnalyzeStage;
import io.github.joke.percolate.processor.stage.BuildValueGraphStage;
import io.github.joke.percolate.processor.stage.DumpResolvedPathsStage;
import io.github.joke.percolate.processor.stage.DumpValueGraphStage;
import io.github.joke.percolate.processor.stage.GenerateStage;
import io.github.joke.percolate.processor.stage.MatchMappingsStage;
import io.github.joke.percolate.processor.stage.OptimizePathStage;
import io.github.joke.percolate.processor.stage.ResolvePathStage;
import io.github.joke.percolate.processor.stage.ValidateMatchingStage;
import io.github.joke.percolate.processor.stage.ValidateResolutionStage;
import jakarta.inject.Inject;
import javax.annotation.processing.Messager;
import javax.lang.model.element.TypeElement;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor(onConstructor_ = @Inject)
final class Pipeline {

    private final AnalyzeStage analyzeStage;
    private final MatchMappingsStage matchMappingsStage;
    private final ValidateMatchingStage validateMatchingStage;
    private final BuildValueGraphStage buildValueGraphStage;
    private final DumpValueGraphStage dumpValueGraphStage;
    private final ResolvePathStage resolvePathStage;
    private final OptimizePathStage optimizePathStage;
    private final DumpResolvedPathsStage dumpResolvedPathsStage;
    private final ValidateResolutionStage validateResolutionStage;
    private final GenerateStage generateStage;
    private final Messager messager;

    @Nullable
    JavaFile process(final TypeElement element) {
        final var analyzeResult = analyzeStage.execute(element);
        if (!analyzeResult.isSuccess()) {
            reportErrors(analyzeResult);
            return null;
        }

        final var matchResult = matchMappingsStage.execute(analyzeResult.value());
        if (!matchResult.isSuccess()) {
            reportErrors(matchResult);
            return null;
        }

        final var validateMatchResult = validateMatchingStage.execute(matchResult.value());
        if (!validateMatchResult.isSuccess()) {
            reportErrors(validateMatchResult);
            return null;
        }

        final var mapperType = validateMatchResult.value().getMapperType();

        final var valueGraphResult = buildValueGraphStage.execute(validateMatchResult.value());
        if (!valueGraphResult.isSuccess()) {
            reportErrors(valueGraphResult);
            return null;
        }

        dumpValueGraphStage.execute(mapperType, valueGraphResult.value());

        final var resolveResult = resolvePathStage.execute(valueGraphResult.value());
        if (!resolveResult.isSuccess()) {
            reportErrors(resolveResult);
            return null;
        }

        final var optimizeResult = optimizePathStage.execute(resolveResult.value());
        if (!optimizeResult.isSuccess()) {
            reportErrors(optimizeResult);
            return null;
        }

        dumpResolvedPathsStage.execute(mapperType, valueGraphResult.value(), optimizeResult.value());

        final var validateResolutionResult = validateResolutionStage.execute(mapperType, optimizeResult.value());
        if (!validateResolutionResult.isSuccess()) {
            reportErrors(validateResolutionResult);
            return null;
        }

        final var generateResult = generateStage.execute(mapperType, validateResolutionResult.value());
        if (!generateResult.isSuccess()) {
            reportErrors(generateResult);
            return null;
        }

        return generateResult.value();
    }

    private void reportErrors(final StageResult<?> result) {
        result.errors()
                .forEach(diagnostic ->
                        messager.printMessage(diagnostic.getKind(), diagnostic.getMessage(), diagnostic.getElement()));
    }
}
