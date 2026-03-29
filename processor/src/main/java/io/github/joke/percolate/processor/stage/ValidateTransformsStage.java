package io.github.joke.percolate.processor.stage;

import static javax.tools.Diagnostic.Kind.ERROR;

import io.github.joke.percolate.processor.Diagnostic;
import io.github.joke.percolate.processor.ErrorMessages;
import io.github.joke.percolate.processor.StageResult;
import io.github.joke.percolate.processor.model.DiscoveredMethod;
import io.github.joke.percolate.processor.transform.ResolvedMapping;
import io.github.joke.percolate.processor.transform.ResolvedModel;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ValidateTransformsStage {

    @Inject
    ValidateTransformsStage() {}

    public StageResult<ResolvedModel> execute(final ResolvedModel resolvedModel) {
        final List<Diagnostic> errors = new ArrayList<>();

        for (final Map.Entry<DiscoveredMethod, List<ResolvedMapping>> entry :
                resolvedModel.getMethodMappings().entrySet()) {
            final var method = entry.getKey();

            for (final ResolvedMapping mapping : entry.getValue()) {
                if (!mapping.isResolved()) {
                    errors.add(new Diagnostic(
                            method.getOriginal().getMethod(),
                            ErrorMessages.unresolvedTransform(
                                    mapping.getSource().getName(),
                                    mapping.getTarget().getName(),
                                    mapping.getSource().getType(),
                                    mapping.getTarget().getType(),
                                    method.getOriginal(),
                                    resolvedModel.getMapperType()),
                            ERROR));
                }
            }
        }

        if (!errors.isEmpty()) {
            return StageResult.failure(errors);
        }

        return StageResult.success(resolvedModel);
    }
}
