package io.github.joke.percolate.processor.stage;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.MapList;
import io.github.joke.percolate.processor.Diagnostic;
import io.github.joke.percolate.processor.StageResult;
import io.github.joke.percolate.processor.model.MapDirective;
import io.github.joke.percolate.processor.model.MapperModel;
import io.github.joke.percolate.processor.model.MappingMethodModel;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic.Kind;

public final class AnalyzeStage {

    @Inject
    AnalyzeStage() {}

    public StageResult<MapperModel> execute(final TypeElement mapperType) {
        final List<Diagnostic> errors = new ArrayList<>();

        final List<MappingMethodModel> methods = mapperType.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.METHOD)
                .filter(e -> e.getModifiers().contains(Modifier.ABSTRACT))
                .map(ExecutableElement.class::cast)
                .map(method -> analyzeMethod(method, errors))
                .collect(Collectors.toList());

        if (!errors.isEmpty()) {
            return StageResult.failure(errors);
        }

        return StageResult.success(new MapperModel(mapperType, methods));
    }

    private MappingMethodModel analyzeMethod(final ExecutableElement method, final List<Diagnostic> errors) {
        if (method.getParameters().isEmpty()) {
            errors.add(new Diagnostic(method, "Mapping method must have a source parameter", Kind.ERROR));
        }

        if (method.getReturnType().getKind() == TypeKind.VOID) {
            errors.add(new Diagnostic(method, "Mapping method must have a non-void return type", Kind.ERROR));
        }

        final var sourceType = method.getParameters().isEmpty()
                ? method.getReturnType()
                : method.getParameters().get(0).asType();
        final var targetType = method.getReturnType();
        final var directives = parseDirectives(method);

        return new MappingMethodModel(method, sourceType, targetType, directives);
    }

    private List<MapDirective> parseDirectives(final ExecutableElement method) {
        final List<MapDirective> directives = new ArrayList<>();

        final MapList mapList = method.getAnnotation(MapList.class);
        if (mapList != null) {
            for (final Map map : mapList.value()) {
                directives.add(new MapDirective(map.source(), map.target()));
            }
            return directives;
        }

        final Map map = method.getAnnotation(Map.class);
        if (map != null) {
            directives.add(new MapDirective(map.source(), map.target()));
        }

        return directives;
    }
}
