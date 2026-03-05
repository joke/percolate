package io.github.joke.percolate.stage;

import static java.util.stream.Collectors.toUnmodifiableList;
import static javax.lang.model.element.Modifier.ABSTRACT;

import io.github.joke.percolate.MapList;
import io.github.joke.percolate.model.MapDirective;
import io.github.joke.percolate.model.MapperDefinition;
import io.github.joke.percolate.model.MethodDefinition;
import io.github.joke.percolate.model.ParameterDefinition;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

public class ParseMapperStage {

    private final Elements elements;

    @Inject
    ParseMapperStage(final Elements elements) {
        this.elements = elements;
    }

    public MapperDefinition execute(final TypeElement typeElement) {
        final var packageName =
                elements.getPackageOf(typeElement).getQualifiedName().toString();
        final var simpleName = typeElement.getSimpleName().toString();
        final var methods = typeElement.getEnclosedElements().stream()
                .filter(element -> element instanceof ExecutableElement)
                .map(element -> (ExecutableElement) element)
                .map(this::parseMethod)
                .collect(toUnmodifiableList());
        return new MapperDefinition(typeElement, packageName, simpleName, methods);
    }

    private MethodDefinition parseMethod(final ExecutableElement method) {
        final var name = method.getSimpleName().toString();
        final var isAbstract = method.getModifiers().contains(ABSTRACT);
        final var parameters = method.getParameters().stream()
                .map(param -> new ParameterDefinition(param.getSimpleName().toString(), param.asType()))
                .collect(toUnmodifiableList());
        final var directives = extractMapDirectives(method);
        return new MethodDefinition(method, name, method.getReturnType(), parameters, isAbstract, directives);
    }

    private List<MapDirective> extractMapDirectives(final ExecutableElement method) {
        final var mapList = method.getAnnotation(MapList.class);
        if (mapList != null) {
            return Arrays.stream(mapList.value())
                    .map(mapping -> new MapDirective(mapping.target(), mapping.source()))
                    .collect(toUnmodifiableList());
        }
        final var map = method.getAnnotation(io.github.joke.percolate.Map.class);
        return map != null ? List.of(new MapDirective(map.target(), map.source())) : List.of();
    }
}
