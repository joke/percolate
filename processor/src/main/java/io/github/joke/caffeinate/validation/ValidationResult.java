package io.github.joke.caffeinate.validation;

import io.github.joke.caffeinate.analysis.AnalysisResult;
import io.github.joke.caffeinate.analysis.MapperDescriptor;
import java.util.List;

public final class ValidationResult {
    private final AnalysisResult analysisResult;
    private final boolean hasFatalErrors;

    public ValidationResult(AnalysisResult analysisResult, boolean hasFatalErrors) {
        this.analysisResult = analysisResult;
        this.hasFatalErrors = hasFatalErrors;
    }

    public List<MapperDescriptor> getMappers() {
        return analysisResult.getMappers();
    }

    public boolean hasFatalErrors() {
        return hasFatalErrors;
    }
}
