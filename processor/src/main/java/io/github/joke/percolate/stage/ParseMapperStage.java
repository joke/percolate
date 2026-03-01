package io.github.joke.percolate.stage;

import static java.util.stream.Collectors.toList;
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
    ParseMapperStage(Elements elements) {
        this.elements = elements;
    }

    public MapperDefinition execute(TypeElement typeElement) {
        String packageName = elements.getPackageOf(typeElement).getQualifiedName().toString();
        String simpleName = typeElement.getSimpleName().toString();
        List<MethodDefinition> methods = typeElement.getEnclosedElements().stream()
                .filter(e -> e instanceof ExecutableElement)
                .map(e -> (ExecutableElement) e)
                .map(this::parseMethod)
                .collect(toList());
        return new MapperDefinition(typeElement, packageName, simpleName, methods);
    }

    private MethodDefinition parseMethod(ExecutableElement method) {
        String name = method.getSimpleName().toString();
        boolean isAbstract = method.getModifiers().contains(ABSTRACT);
        List<ParameterDefinition> parameters = method.getParameters().stream()
                .map(param -> new ParameterDefinition(param.getSimpleName().toString(), param.asType()))
                .collect(toList());
        List<MapDirective> directives = extractMapDirectives(method);
        return new MethodDefinition(method, name, method.getReturnType(), parameters, isAbstract, directives);
    }

    private List<MapDirective> extractMapDirectives(ExecutableElement method) {
        MapList mapList = method.getAnnotation(MapList.class);
        if (mapList != null) {
            return Arrays.stream(mapList.value())
                    .map(m -> new MapDirective(m.target(), m.source()))
                    .collect(toList());
        }
        io.github.joke.percolate.Map map = method.getAnnotation(io.github.joke.percolate.Map.class);
        return map != null
                ? List.of(new MapDirective(map.target(), map.source()))
                : List.of();
    }
}
