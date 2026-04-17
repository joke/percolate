package io.github.joke.percolate.processor.stage;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toUnmodifiableList;
import static javax.tools.Diagnostic.Kind.ERROR;

import io.github.joke.percolate.MapOptKey;
import io.github.joke.percolate.processor.Diagnostic;
import io.github.joke.percolate.processor.ErrorMessages;
import io.github.joke.percolate.processor.StageResult;
import io.github.joke.percolate.processor.graph.TargetSlotNode;
import io.github.joke.percolate.processor.match.MappingAssignment;
import io.github.joke.percolate.processor.match.MethodMatching;
import io.github.joke.percolate.processor.match.ResolutionFailure;
import io.github.joke.percolate.processor.match.ResolvedAssignment;
import io.github.joke.percolate.processor.model.MappingMethodModel;
import io.github.joke.percolate.processor.model.ReadAccessor;
import io.github.joke.percolate.processor.model.WriteAccessor;
import io.github.joke.percolate.processor.spi.SourcePropertyDiscovery;
import io.github.joke.percolate.processor.spi.TargetPropertyDiscovery;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Validates the resolution layer: checks that every {@link ResolvedAssignment} either has a
 * resolved path or carries a diagnosable failure, and reports any completely unmapped target
 * properties.
 *
 * <p>Checks performed, in order:
 * <ol>
 *   <li>Source access failures — {@link ResolutionFailure} recorded by {@code BuildValueGraphStage}.</li>
 *   <li>Unknown target property — target slot name not found via {@link TargetPropertyDiscovery}.</li>
 *   <li>Unresolved type gap — assigned target exists but no type conversion path was found.</li>
 *   <li>Unmapped target properties — target properties with no assignment at all.</li>
 * </ol>
 *
 * <p>Duplicate-target diagnostics are NOT produced here; that detection lives in
 * {@code ValidateMatchingStage}.
 */
@RequiredArgsConstructor
public final class ValidateResolutionStage {

    private final Types types;
    private final Elements elements;
    private final List<TargetPropertyDiscovery> targetDiscoveries;
    private final List<SourcePropertyDiscovery> sourceDiscoveries;

    @Inject
    ValidateResolutionStage(final Types types, final Elements elements) {
        this(
                types,
                elements,
                loadAndSortByPriority(TargetPropertyDiscovery.class, TargetPropertyDiscovery::priority),
                loadAndSortByPriority(SourcePropertyDiscovery.class, SourcePropertyDiscovery::priority));
    }

    public StageResult<Map<MethodMatching, List<ResolvedAssignment>>> execute(
            final TypeElement mapperType, final Map<MethodMatching, List<ResolvedAssignment>> resolvedAssignments) {

        final List<Diagnostic> errors = new ArrayList<>();

        for (final var entry : resolvedAssignments.entrySet()) {
            validateMethod(entry.getKey(), entry.getValue(), mapperType, errors);
        }

        if (!errors.isEmpty()) {
            return StageResult.failure(errors);
        }
        return StageResult.success(resolvedAssignments);
    }

    private void validateMethod(
            final MethodMatching matching,
            final List<ResolvedAssignment> assignments,
            final TypeElement mapperType,
            final List<Diagnostic> errors) {

        final var model = matching.getModel();
        final Map<String, WriteAccessor> targetProps = discoverTargetPropertyMap(model.getTargetType());
        final Set<String> assignedTargetNames = new LinkedHashSet<>();

        for (final var ra : assignments) {
            final var assignment = ra.getAssignment();
            assignedTargetNames.add(assignment.getTargetName());

            if (ra.getFailure() != null) {
                errors.add(buildAccessFailureDiagnostic(ra.getFailure(), model));
            } else if (ra.getPath() == null) {
                final @Nullable WriteAccessor targetAccessor = targetProps.get(assignment.getTargetName());
                if (targetAccessor == null) {
                    errors.add(new Diagnostic(
                            model.getMethod(),
                            ErrorMessages.unknownTargetProperty(
                                    assignment.getTargetName(), model, targetProps.keySet()),
                            ERROR));
                } else {
                    final @Nullable Diagnostic ambiguity =
                            detectAmbiguity(ra, assignment, model, mapperType, targetAccessor);
                    errors.add(
                            ambiguity != null
                                    ? ambiguity
                                    : buildTypeGapDiagnostic(assignment, model, mapperType, targetAccessor));
                }
            }

            validateOptions(ra, targetProps.get(assignment.getTargetName()), model, errors);
        }

        // Report target properties that received no assignment at all.
        for (final var targetName : targetProps.keySet()) {
            if (!assignedTargetNames.contains(targetName)) {
                errors.add(new Diagnostic(
                        model.getMethod(),
                        ErrorMessages.unmappedTargetProperty(targetName, mapperType, Set.of()),
                        ERROR));
            }
        }
    }

    private static final Set<String> DURATION_PERIOD_TYPES = Set.of("java.time.Duration", "java.time.Period");

    private static final String JAVA_LANG_STRING = "java.lang.String";

    /**
     * Validates {@link MapOptKey} options per assignment. DATE_FORMAT requires at least one side
     * to be {@code java.lang.String} and rejects {@code Duration}/{@code Period} on either side.
     */
    private static void validateOptions(
            final ResolvedAssignment ra,
            final @Nullable WriteAccessor targetAccessor,
            final MappingMethodModel model,
            final List<Diagnostic> errors) {
        final var assignment = ra.getAssignment();
        if (!assignment.getOptions().containsKey(MapOptKey.DATE_FORMAT) || targetAccessor == null) {
            return;
        }

        final var sourceType = sourceLeafType(ra, model);
        final var targetType = targetAccessor.getType().toString();

        if (DURATION_PERIOD_TYPES.contains(sourceType)) {
            errors.add(new Diagnostic(
                    model.getMethod(),
                    ErrorMessages.dateFormatOnTemporalWithoutAccessor(
                            String.join(".", assignment.getSourcePath()),
                            assignment.getTargetName(),
                            sourceType,
                            model),
                    ERROR));
            return;
        }
        if (DURATION_PERIOD_TYPES.contains(targetType)) {
            errors.add(new Diagnostic(
                    model.getMethod(),
                    ErrorMessages.dateFormatOnTemporalWithoutAccessor(
                            String.join(".", assignment.getSourcePath()),
                            assignment.getTargetName(),
                            targetType,
                            model),
                    ERROR));
            return;
        }

        if (!JAVA_LANG_STRING.equals(sourceType) && !JAVA_LANG_STRING.equals(targetType)) {
            errors.add(new Diagnostic(
                    model.getMethod(),
                    ErrorMessages.dateFormatOnNonStringMapping(
                            String.join(".", assignment.getSourcePath()), assignment.getTargetName(), model),
                    ERROR));
        }
    }

    /**
     * Returns the type-string of the source leaf (the penultimate vertex on the resolved path)
     * when available, falling back to the method-level source type otherwise.
     */
    private static String sourceLeafType(final ResolvedAssignment ra, final MappingMethodModel model) {
        final var path = ra.getPath();
        if (path == null) {
            return model.getSourceType().toString();
        }
        final var vertices = path.getVertexList();
        // last vertex is TargetSlotNode; penultimate carries the source-leaf type
        for (int i = vertices.size() - 1; i >= 0; i--) {
            final var v = vertices.get(i);
            if (!(v instanceof TargetSlotNode)) {
                return v.getType().toString();
            }
        }
        return model.getSourceType().toString();
    }

    /** Source access chain failed — a segment name was not found on the context type. */
    private static Diagnostic buildAccessFailureDiagnostic(
            final ResolutionFailure failure, final MappingMethodModel model) {
        return new Diagnostic(
                model.getMethod(),
                ErrorMessages.unknownSourceProperty(failure.getSegmentName(), model, failure.getAvailableProperties()),
                ERROR);
    }

    /**
     * Detects sibling-method ambiguity when the path is unresolved: two or more methods on the
     * mapper would match {@code sourceLeafType → targetType}, and none strictly dominates the
     * rest by parameter or return-type specificity.
     */
    private @Nullable Diagnostic detectAmbiguity(
            final ResolvedAssignment ra,
            final MappingAssignment assignment,
            final MappingMethodModel model,
            final TypeElement mapperType,
            final WriteAccessor targetAccessor) {
        final var using = assignment.getUsing();
        final var sourceLeaf = resolveSourceLeafType(ra, assignment, model);
        final var targetType = targetAccessor.getType();
        final var currentMethod = model.getMethod();

        final List<javax.lang.model.element.ExecutableElement> candidates = elements.getAllMembers(mapperType).stream()
                .filter(e -> e.getKind() == javax.lang.model.element.ElementKind.METHOD)
                .map(e -> (javax.lang.model.element.ExecutableElement) e)
                .filter(m -> m.getParameters().size() == 1)
                .filter(m -> m.getReturnType().getKind() != javax.lang.model.type.TypeKind.VOID)
                .filter(m -> !m.equals(currentMethod))
                .filter(m -> using == null || m.getSimpleName().toString().equals(using))
                .filter(m ->
                        types.isAssignable(sourceLeaf, m.getParameters().get(0).asType()))
                .filter(m -> types.isAssignable(m.getReturnType(), targetType))
                .collect(toUnmodifiableList());

        if (candidates.size() < 2) {
            return null;
        }

        final var bestByParam = candidates.stream()
                .filter(a -> candidates.stream()
                        .noneMatch(b -> !b.equals(a)
                                && types.isAssignable(
                                        b.getParameters().get(0).asType(),
                                        a.getParameters().get(0).asType())
                                && !types.isAssignable(
                                        a.getParameters().get(0).asType(),
                                        b.getParameters().get(0).asType())))
                .collect(toUnmodifiableList());
        final var best = bestByParam.size() > 1
                ? bestByParam.stream()
                        .filter(a -> bestByParam.stream()
                                .noneMatch(b -> !b.equals(a)
                                        && types.isAssignable(b.getReturnType(), a.getReturnType())
                                        && !types.isAssignable(a.getReturnType(), b.getReturnType())))
                        .collect(toUnmodifiableList())
                : bestByParam;

        if (best.size() < 2) {
            return null;
        }

        final List<String> descriptions = best.stream()
                .map(m -> m.getSimpleName() + "(" + m.getParameters().get(0).asType() + ") -> " + m.getReturnType())
                .collect(toUnmodifiableList());
        return new Diagnostic(
                currentMethod,
                ErrorMessages.ambiguousMethodCandidates(
                        String.join(".", assignment.getSourcePath()), assignment.getTargetName(), descriptions),
                ERROR);
    }

    private TypeMirror resolveSourceLeafType(
            final ResolvedAssignment ra, final MappingAssignment assignment, final MappingMethodModel model) {
        final var path = ra.getPath();
        if (path != null) {
            final var vertices = path.getVertexList();
            for (int i = vertices.size() - 1; i >= 0; i--) {
                final var v = vertices.get(i);
                if (!(v instanceof TargetSlotNode)) {
                    return v.getType();
                }
            }
        }
        TypeMirror current = model.getSourceType();
        for (final var segment : assignment.getSourcePath()) {
            final var props = discoverSourcePropertyMap(current);
            final ReadAccessor accessor = props.get(segment);
            if (accessor == null) {
                return current;
            }
            current = accessor.getType();
        }
        return current;
    }

    private Map<String, ReadAccessor> discoverSourcePropertyMap(final TypeMirror type) {
        final Map<String, ReadAccessor> merged = new LinkedHashMap<>();
        final Map<String, Integer> priorities = new LinkedHashMap<>();
        for (final var discovery : sourceDiscoveries) {
            for (final var accessor : discovery.discover(type, elements, types)) {
                final int current = priorities.getOrDefault(accessor.getName(), Integer.MIN_VALUE);
                if (discovery.priority() > current) {
                    merged.put(accessor.getName(), accessor);
                    priorities.put(accessor.getName(), discovery.priority());
                }
            }
        }
        return merged;
    }

    /** Target accessor exists but no type conversion path was found. */
    @SuppressWarnings("NullAway") // using is @Nullable but unresolvedTransform accepts "" for absent
    private static Diagnostic buildTypeGapDiagnostic(
            final MappingAssignment assignment,
            final MappingMethodModel model,
            final TypeElement mapperType,
            final WriteAccessor targetAccessor) {
        final var using = assignment.getUsing();
        return new Diagnostic(
                model.getMethod(),
                ErrorMessages.unresolvedTransform(
                        String.join(".", assignment.getSourcePath()),
                        assignment.getTargetName(),
                        model.getSourceType(),
                        targetAccessor.getType(),
                        model,
                        mapperType,
                        using != null ? using : ""),
                ERROR);
    }

    private Map<String, WriteAccessor> discoverTargetPropertyMap(final TypeMirror type) {
        final Map<String, WriteAccessor> merged = new LinkedHashMap<>();
        final Map<String, Integer> priorities = new LinkedHashMap<>();
        for (final TargetPropertyDiscovery discovery : targetDiscoveries) {
            for (final WriteAccessor accessor : discovery.discover(type, elements, types)) {
                final int current = priorities.getOrDefault(accessor.getName(), Integer.MIN_VALUE);
                if (discovery.priority() > current) {
                    merged.put(accessor.getName(), accessor);
                    priorities.put(accessor.getName(), discovery.priority());
                }
            }
        }
        return merged;
    }

    private static <T> List<T> loadAndSortByPriority(
            final Class<T> serviceClass, final java.util.function.ToIntFunction<T> priorityFn) {
        return ServiceLoader.load(serviceClass, serviceClass.getClassLoader()).stream()
                .map(ServiceLoader.Provider::get)
                .sorted(comparingInt((T s) -> -priorityFn.applyAsInt(s)))
                .collect(toUnmodifiableList());
    }
}
