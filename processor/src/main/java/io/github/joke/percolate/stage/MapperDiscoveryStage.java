package io.github.joke.percolate.stage;

import static java.util.stream.Collectors.toList;
import static javax.lang.model.element.ElementKind.INTERFACE;
import static javax.tools.Diagnostic.Kind.ERROR;

import io.github.joke.percolate.Mapper;
import io.github.joke.percolate.di.RoundScoped;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

@RoundScoped
public class MapperDiscoveryStage {

    private final Messager messager;

    @Inject
    MapperDiscoveryStage(Messager messager) {
        this.messager = messager;
    }

    public List<TypeElement> execute(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return roundEnv.getElementsAnnotatedWith(Mapper.class).stream()
                .filter(this::validateIsInterface)
                .map(element -> (TypeElement) element)
                .collect(toList());
    }

    private boolean validateIsInterface(Element element) {
        if (element.getKind() != INTERFACE) {
            messager.printMessage(ERROR, "@Mapper can only be applied to interfaces", element);
            return false;
        }
        return true;
    }
}
