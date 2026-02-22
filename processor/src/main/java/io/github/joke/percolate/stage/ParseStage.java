package io.github.joke.percolate.stage;

import static java.util.stream.Collectors.toList;
import static javax.lang.model.element.ElementKind.INTERFACE;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.tools.Diagnostic.Kind.ERROR;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.MapList;
import io.github.joke.percolate.Mapper;
import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.model.MapDirective;
import io.github.joke.percolate.model.MapperDefinition;
import io.github.joke.percolate.model.MethodDefinition;
import io.github.joke.percolate.model.ParameterDefinition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

@RoundScoped
public class ParseStage {

    private final Elements elements;
    private final Messager messager;

    @Inject
    ParseStage(Elements elements, Messager messager) {
        this.elements = elements;
        this.messager = messager;
    }

    public ParseResult execute(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        List<MapperDefinition> mappers = roundEnv.getElementsAnnotatedWith(Mapper.class).stream()
                .filter(this::validateIsInterface)
                .map(element -> (TypeElement) element)
                .map(this::parseMapper)
                .collect(toList());
        return new ParseResult(mappers);
    }

    private boolean validateIsInterface(Element element) {
        if (element.getKind() != INTERFACE) {
            messager.printMessage(ERROR, "@Mapper can only be applied to interfaces", element);
            return false;
        }
        return true;
    }

    private MapperDefinition parseMapper(TypeElement typeElement) {
        String packageName =
                elements.getPackageOf(typeElement).getQualifiedName().toString();
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
        List<MapDirective> directives = new ArrayList<>();

        // Handle @MapList container (multiple @Map annotations)
        MapList mapList = method.getAnnotation(MapList.class);
        if (mapList != null) {
            Arrays.stream(mapList.value())
                    .map(m -> new MapDirective(m.target(), m.source()))
                    .forEach(directives::add);
            return directives;
        }

        // Handle single @Map annotation
        Map map = method.getAnnotation(Map.class);
        if (map != null) {
            directives.add(new MapDirective(map.target(), map.source()));
        }

        return directives;
    }
}
