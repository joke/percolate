package io.github.joke.percolate.processor.stage;

import static javax.tools.Diagnostic.Kind.ERROR;

import io.github.joke.percolate.processor.Diagnostic;
import io.github.joke.percolate.processor.ErrorMessages;
import io.github.joke.percolate.processor.StageResult;
import io.github.joke.percolate.processor.model.MappingMethodModel;
import io.github.joke.percolate.processor.transform.ResolvedMapping;
import io.github.joke.percolate.processor.transform.ResolvedModel;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ValidateTransformsStage {

    @Inject
    ValidateTransformsStage() {}

    public StageResult<ResolvedModel> execute(final ResolvedModel resolvedModel) {
        final List<Diagnostic> errors = new ArrayList<>();

        for (final var entry : resolvedModel.getMethodMappings().entrySet()) {
            final var method = entry.getKey();
            validateMappings(method, entry.getValue(), resolvedModel, errors);
        }

        for (final var entry : resolvedModel.getUnmappedTargets().entrySet()) {
            final var method = entry.getKey();
            for (final var targetName : entry.getValue()) {
                errors.add(new Diagnostic(
                        method.getMethod(),
                        ErrorMessages.unmappedTargetProperty(targetName, resolvedModel.getMapperType(), Set.of()),
                        ERROR));
            }
        }

        for (final var entry : resolvedModel.getDuplicateTargets().entrySet()) {
            final var method = entry.getKey();
            for (final var dup : entry.getValue().entrySet()) {
                errors.add(new Diagnostic(
                        method.getMethod(),
                        ErrorMessages.conflictingMappings(dup.getKey(), resolvedModel.getMapperType(), dup.getValue()),
                        ERROR));
            }
        }

        if (!errors.isEmpty()) {
            return StageResult.failure(errors);
        }

        return StageResult.success(resolvedModel);
    }

    private static void validateMappings(
            final MappingMethodModel method,
            final List<ResolvedMapping> mappings,
            final ResolvedModel resolvedModel,
            final List<Diagnostic> errors) {
        for (final ResolvedMapping mapping : mappings) {
            if (mapping.getFailure() != null) {
                errors.add(buildAccessFailureDiagnostic(method, mapping));
            } else if (!mapping.isResolved()) {
                errors.add(buildUnresolvedTransformDiagnostic(method, mapping, resolvedModel));
            }
        }
    }

    @SuppressWarnings("NullAway") // failure is non-null at call site — checked in validateMappings
    private static Diagnostic buildAccessFailureDiagnostic(
            final MappingMethodModel method, final ResolvedMapping mapping) {
        final var failure = mapping.getFailure();
        final var message = isTargetFailure(mapping)
                ? ErrorMessages.unknownTargetProperty(
                        failure.getSegmentName(), method, failure.getAvailableProperties())
                : ErrorMessages.unknownSourceProperty(
                        failure.getSegmentName(), method, failure.getAvailableProperties());
        return new Diagnostic(method.getMethod(), message, ERROR);
    }

    @SuppressWarnings("NullAway") // targetAccessor is non-null when failure==null and path==null
    private static Diagnostic buildUnresolvedTransformDiagnostic(
            final MappingMethodModel method, final ResolvedMapping mapping, final ResolvedModel resolvedModel) {
        final var sourceType = mapping.getSourceChain().isEmpty()
                ? method.getSourceType()
                : mapping.getSourceChain()
                        .get(mapping.getSourceChain().size() - 1)
                        .getType();
        final var targetType = mapping.getTargetAccessor().getType();
        return new Diagnostic(
                method.getMethod(),
                ErrorMessages.unresolvedTransform(
                        mapping.getSourceName(),
                        mapping.getTargetName(),
                        sourceType,
                        targetType,
                        method,
                        resolvedModel.getMapperType()),
                ERROR);
    }

    private static boolean isTargetFailure(final ResolvedMapping mapping) {
        return !mapping.getSourceChain().isEmpty();
    }
}
