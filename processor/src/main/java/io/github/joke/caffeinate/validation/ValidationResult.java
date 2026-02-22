package io.github.joke.caffeinate.validation;

import io.github.joke.caffeinate.analysis.MapperDescriptor;
import io.github.joke.caffeinate.resolution.ResolutionResult;
import io.github.joke.caffeinate.resolution.ResolvedMapperDescriptor;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class ValidationResult {
    private final ResolutionResult resolutionResult;

    @Nullable
    private final List<MapperDescriptor> legacyMappers;

    private final boolean hasFatalErrors;

    public ValidationResult(ResolutionResult resolutionResult, boolean hasFatalErrors) {
        this.resolutionResult = resolutionResult;
        this.legacyMappers = null;
        this.hasFatalErrors = hasFatalErrors;
    }

    /** Constructor for deprecated validate(AnalysisResult) path during transition. */
    public ValidationResult(
            ResolutionResult resolutionResult, @Nullable List<MapperDescriptor> legacyMappers, boolean hasFatalErrors) {
        this.resolutionResult = resolutionResult;
        this.legacyMappers = legacyMappers;
        this.hasFatalErrors = hasFatalErrors;
    }

    public ResolutionResult getResolutionResult() {
        return resolutionResult;
    }

    /**
     * Legacy API for backward compatibility. GraphStage and CodeGenStage still expect
     * MapperDescriptor. During the transition, this returns either the legacy mappers (if from
     * deprecated validate(AnalysisResult) path) or adapted resolved mappers.
     *
     * @deprecated Use {@link #getResolutionResult()} after Task 7 to access resolved types directly.
     */
    @Deprecated
    public List<MapperDescriptor> getMappers() {
        // If this result came from the deprecated path, return the legacy mappers
        if (legacyMappers != null) {
            return legacyMappers;
        }
        // Convert ResolvedMapperDescriptor to MapperDescriptor for downstream compatibility
        // TODO(Task 7): Remove this conversion after GraphStage and CodeGenStage are updated
        List<MapperDescriptor> legacy = new ArrayList<>();
        for (ResolvedMapperDescriptor resolved : resolutionResult.getMappers()) {
            // Create a MapperDescriptor with empty methods - they won't be used during transition
            legacy.add(new MapperDescriptor(resolved.getMapperInterface(), List.of()));
        }
        return legacy;
    }

    public boolean hasFatalErrors() {
        return hasFatalErrors;
    }
}
