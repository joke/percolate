package io.github.joke.percolate.processor.stage;

import static java.util.stream.Collectors.toUnmodifiableSet;

import io.github.joke.percolate.processor.StageResult;
import io.github.joke.percolate.processor.match.AssignmentOrigin;
import io.github.joke.percolate.processor.match.MappingAssignment;
import io.github.joke.percolate.processor.match.MatchedModel;
import io.github.joke.percolate.processor.match.MethodMatching;
import io.github.joke.percolate.processor.model.MapDirective;
import io.github.joke.percolate.processor.model.MapperModel;
import io.github.joke.percolate.processor.model.MappingMethodModel;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import lombok.RequiredArgsConstructor;

/**
 * Converts the parsed {@link MapperModel} into a {@link MatchedModel} by:
 * <ol>
 *   <li>Emitting one {@link MappingAssignment} per explicit {@code @Map} / {@code @MapList} directive.</li>
 *   <li>Filling gaps via auto-mapping: for every target property not already claimed by a directive,
 *       if the source type exposes a top-level property of the same name, emit an {@code AUTO_MAPPED} assignment.</li>
 * </ol>
 *
 * <p>The stage does NOT call property-discovery SPIs and does NOT resolve {@link javax.lang.model.type.TypeMirror}s.
 * It operates at the name level only.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class MatchMappingsStage {

    @SuppressWarnings("unused") // available for future name-resolution needs
    private final Elements elements;

    @SuppressWarnings("unused") // available for future name-resolution needs
    private final Types types;

    public StageResult<MatchedModel> execute(final MapperModel mapperModel) {
        final List<MethodMatching> methodMatchings = new ArrayList<>();

        for (final MappingMethodModel method : mapperModel.getMethods()) {
            methodMatchings.add(matchMethod(method));
        }

        return StageResult.success(new MatchedModel(mapperModel.getMapperType(), methodMatchings));
    }

    private MethodMatching matchMethod(final MappingMethodModel method) {
        final List<MappingAssignment> assignments = new ArrayList<>();
        final Set<String> claimedTargets = new LinkedHashSet<>();

        // 1. Explicit @Map / @MapList directives — in source-declaration order.
        for (final MapDirective directive : method.getDirectives()) {
            final var sourcePath = Arrays.asList(directive.getSource().split("\\.", -1));
            final var using = directive.getUsing();
            final var origin =
                    (using != null && !using.isEmpty()) ? AssignmentOrigin.USING_ROUTED : AssignmentOrigin.EXPLICIT_MAP;
            assignments.add(
                    MappingAssignment.of(sourcePath, directive.getTarget(), directive.getOptions(), using, origin));
            claimedTargets.add(directive.getTarget());
        }

        // 2. Auto-mapping: unmatched target properties that have a same-named top-level source property.
        final var targetNames = scanTargetPropertyNames(method.getTargetType());
        final var sourceNames = scanSourcePropertyNames(method.getSourceType());

        for (final String targetName : targetNames) {
            if (!claimedTargets.contains(targetName) && sourceNames.contains(targetName)) {
                assignments.add(MappingAssignment.of(
                        List.of(targetName),
                        targetName,
                        java.util.Collections.emptyMap(),
                        null,
                        AssignmentOrigin.AUTO_MAPPED));
            }
        }

        return new MethodMatching(method.getMethod(), method, assignments);
    }

    /**
     * Returns the set of property names discoverable on the target type (from getters, fields, and constructor params).
     * Name-level only — no type resolution.
     */
    private static Set<String> scanTargetPropertyNames(final TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return Set.of();
        }
        final var typeElement = (TypeElement) ((DeclaredType) type).asElement();
        final var fromGettersAndFields = typeElement.getEnclosedElements().stream()
                .filter(e -> !e.getModifiers().contains(Modifier.STATIC))
                .flatMap(e -> extractPropertyName(e).stream())
                .collect(toUnmodifiableSet());
        final var fromConstructor = scanConstructorParamNames(typeElement);
        final var result = new LinkedHashSet<>(fromGettersAndFields);
        result.addAll(fromConstructor);
        return Set.copyOf(result);
    }

    /**
     * Returns the set of property names discoverable on the source type (from getters and public fields).
     * Name-level only — no type resolution.
     */
    private static Set<String> scanSourcePropertyNames(final TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return Set.of();
        }
        final var typeElement = (TypeElement) ((DeclaredType) type).asElement();
        return typeElement.getEnclosedElements().stream()
                .filter(e -> !e.getModifiers().contains(Modifier.STATIC))
                .flatMap(e -> extractPropertyName(e).stream())
                .collect(toUnmodifiableSet());
    }

    private static Set<String> scanConstructorParamNames(final TypeElement typeElement) {
        return typeElement.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR)
                .map(e -> (ExecutableElement) e)
                .max(java.util.Comparator.comparingInt(c -> c.getParameters().size()))
                .map(c -> c.getParameters().stream()
                        .map(p -> p.getSimpleName().toString())
                        .collect(toUnmodifiableSet()))
                .orElse(Set.of());
    }

    private static Optional<String> extractPropertyName(final javax.lang.model.element.Element element) {
        if (element.getKind() == ElementKind.METHOD) {
            final var method = (ExecutableElement) element;
            if (!method.getModifiers().contains(Modifier.PUBLIC)
                    || !method.getParameters().isEmpty()
                    || method.getReturnType().getKind() == javax.lang.model.type.TypeKind.VOID) {
                return Optional.empty();
            }
            return getterName(method.getSimpleName().toString());
        }
        if (element.getKind() == ElementKind.FIELD) {
            final var field = (VariableElement) element;
            if (field.getModifiers().contains(Modifier.PUBLIC)) {
                return Optional.of(element.getSimpleName().toString());
            }
        }
        return Optional.empty();
    }

    private static Optional<String> getterName(final String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return Optional.of(Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4));
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return Optional.of(Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3));
        }
        return Optional.empty();
    }
}
