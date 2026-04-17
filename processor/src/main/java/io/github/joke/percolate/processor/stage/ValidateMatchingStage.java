package io.github.joke.percolate.processor.stage;

import static java.util.stream.Collectors.toUnmodifiableSet;
import static javax.tools.Diagnostic.Kind.ERROR;

import io.github.joke.percolate.processor.Diagnostic;
import io.github.joke.percolate.processor.ErrorMessages;
import io.github.joke.percolate.processor.StageResult;
import io.github.joke.percolate.processor.match.MappingAssignment;
import io.github.joke.percolate.processor.match.MatchedModel;
import io.github.joke.percolate.processor.match.MethodMatching;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import lombok.NoArgsConstructor;

/**
 * Validates the matching layer against {@link MatchedModel} without requiring a {@code ValueGraph}.
 *
 * <p>Checks performed:
 * <ol>
 *   <li>Duplicate {@code @Map} directives targeting the same slot on the same method.</li>
 *   <li>Unknown source-root parameter for multi-parameter methods.</li>
 *   <li>Unresolved {@code using=} method references (method not found on the mapper).</li>
 * </ol>
 */
@NoArgsConstructor(onConstructor_ = @Inject)
public final class ValidateMatchingStage {

    public StageResult<MatchedModel> execute(final MatchedModel matchedModel) {
        final List<Diagnostic> errors = new ArrayList<>();

        for (final MethodMatching method : matchedModel.getMethods()) {
            validateDuplicateTargets(method, matchedModel.getMapperType(), errors);
            validateSourceRootParameters(method, errors);
            validateUsingMethods(method, matchedModel.getMapperType(), errors);
        }

        if (!errors.isEmpty()) {
            return StageResult.failure(errors);
        }
        return StageResult.success(matchedModel);
    }

    /** Detects two assignments that target the same property slot. */
    private static void validateDuplicateTargets(
            final MethodMatching method, final TypeElement mapperType, final List<Diagnostic> errors) {
        final Map<String, Set<String>> targetToSources = new LinkedHashMap<>();

        for (final MappingAssignment assignment : method.getAssignments()) {
            targetToSources
                    .computeIfAbsent(assignment.getTargetName(), k -> new LinkedHashSet<>())
                    .add(String.join(".", assignment.getSourcePath()));
        }

        for (final Map.Entry<String, Set<String>> entry : targetToSources.entrySet()) {
            if (entry.getValue().size() > 1) {
                errors.add(new Diagnostic(
                        method.getMethod(),
                        ErrorMessages.conflictingMappings(entry.getKey(), mapperType, entry.getValue()),
                        ERROR));
            }
        }
    }

    /**
     * For multi-parameter mapper methods, the first segment of each source path must match a parameter name.
     * Single-parameter methods are skipped (the single parameter is the implicit root).
     */
    private static void validateSourceRootParameters(final MethodMatching method, final List<Diagnostic> errors) {
        final var params = method.getMethod().getParameters();
        if (params.size() <= 1) {
            return;
        }

        final Set<String> paramNames = new LinkedHashSet<>();
        for (final var param : params) {
            paramNames.add(param.getSimpleName().toString());
        }

        for (final MappingAssignment assignment : method.getAssignments()) {
            final var root = assignment.getSourcePath().get(0);
            if (!paramNames.contains(root)) {
                errors.add(new Diagnostic(
                        method.getMethod(),
                        "Source parameter '" + root + "' not found on method '"
                                + method.getMethod().getSimpleName()
                                + "'. Available parameters: " + new TreeSet<>(paramNames),
                        ERROR));
            }
        }
    }

    /** Verifies that each {@code using=} name resolves to an actual method on the mapper. */
    private static void validateUsingMethods(
            final MethodMatching method, final TypeElement mapperType, final List<Diagnostic> errors) {
        final Set<String> mapperMethodNames = mapperType.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.METHOD)
                .map(e -> e.getSimpleName().toString())
                .collect(toUnmodifiableSet());

        for (final MappingAssignment assignment : method.getAssignments()) {
            final var using = assignment.getUsing();
            if (using == null || mapperMethodNames.contains(using)) {
                continue;
            }

            final var suggestion = suggestMethodName(using, mapperMethodNames)
                    .map(s -> " Did you mean: '" + s + "'?")
                    .orElse("");
            errors.add(new Diagnostic(
                    method.getMethod(),
                    "Helper method (using = \"" + using + "\") not found on mapper " + mapperType + "." + suggestion,
                    ERROR));
        }
    }

    private static Optional<String> suggestMethodName(final String name, final Set<String> candidates) {
        final int threshold = Math.min(4, name.length() / 2);
        return candidates.stream()
                .filter(c -> ErrorMessages.levenshtein(name, c) <= threshold)
                .min((a, b) -> Integer.compare(ErrorMessages.levenshtein(name, a), ErrorMessages.levenshtein(name, b)));
    }
}
