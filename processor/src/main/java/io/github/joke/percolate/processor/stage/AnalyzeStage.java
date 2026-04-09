package io.github.joke.percolate.processor.stage;

import static java.util.stream.Collectors.toUnmodifiableList;
import static javax.lang.model.element.ElementKind.METHOD;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.type.TypeKind.VOID;
import static javax.tools.Diagnostic.Kind.ERROR;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.MapList;
import io.github.joke.percolate.processor.Diagnostic;
import io.github.joke.percolate.processor.StageResult;
import io.github.joke.percolate.processor.model.MapDirective;
import io.github.joke.percolate.processor.model.MapperModel;
import io.github.joke.percolate.processor.model.MappingMethodModel;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import lombok.NoArgsConstructor;

@NoArgsConstructor(onConstructor_ = @Inject)
public final class AnalyzeStage {

    public StageResult<MapperModel> execute(final TypeElement mapperType) {
        final List<Diagnostic> errors = new ArrayList<>();

        final List<MappingMethodModel> methods = mapperType.getEnclosedElements().stream()
                .filter(e -> e.getKind() == METHOD)
                .filter(e -> e.getModifiers().contains(ABSTRACT))
                .map(ExecutableElement.class::cast)
                .map(method -> analyzeMethod(method, errors))
                .collect(toUnmodifiableList());

        if (!errors.isEmpty()) {
            return StageResult.failure(errors);
        }

        return StageResult.success(new MapperModel(mapperType, methods));
    }

    private MappingMethodModel analyzeMethod(final ExecutableElement method, final List<Diagnostic> errors) {
        if (method.getParameters().isEmpty()) {
            errors.add(new Diagnostic(method, "Mapping method must have a source parameter", ERROR));
        }

        if (method.getReturnType().getKind() == VOID) {
            errors.add(new Diagnostic(method, "Mapping method must have a non-void return type", ERROR));
        }

        final var sourceType = method.getParameters().isEmpty()
                ? method.getReturnType()
                : method.getParameters().get(0).asType();
        final var targetType = method.getReturnType();
        final var directives = parseDirectives(method);

        return new MappingMethodModel(method, sourceType, targetType, directives);
    }

    private List<MapDirective> parseDirectives(final ExecutableElement method) {
        final MapList mapList = method.getAnnotation(MapList.class);
        if (mapList != null) {
            return Arrays.stream(mapList.value())
                    .map(map -> new MapDirective(map.source(), map.target()))
                    .collect(toUnmodifiableList());
        }

        final Map map = method.getAnnotation(Map.class);
        if (map != null) {
            return List.of(new MapDirective(map.source(), map.target()));
        }

        return List.of();
    }
}
