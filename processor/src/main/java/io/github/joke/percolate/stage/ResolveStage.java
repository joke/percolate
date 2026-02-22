package io.github.joke.percolate.stage;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.model.MapDirective;
import io.github.joke.percolate.model.MapperDefinition;
import io.github.joke.percolate.model.MethodDefinition;
import io.github.joke.percolate.model.Property;
import io.github.joke.percolate.spi.PropertyDiscoveryStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.jspecify.annotations.Nullable;

@RoundScoped
public class ResolveStage {

    private final ProcessingEnvironment processingEnv;
    private final List<PropertyDiscoveryStrategy> strategies;

    @Inject
    ResolveStage(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.strategies = new ArrayList<>();
        ServiceLoader.load(PropertyDiscoveryStrategy.class, getClass().getClassLoader())
                .forEach(strategies::add);
    }

    public ResolveResult execute(ParseResult parseResult) {
        List<MapperDefinition> resolved =
                parseResult.getMappers().stream().map(this::resolveMapper).collect(toList());
        return new ResolveResult(resolved);
    }

    private MapperDefinition resolveMapper(MapperDefinition mapper) {
        List<MethodDefinition> resolvedMethods =
                mapper.getMethods().stream().map(this::resolveMethod).collect(toList());
        return mapper.withMethods(resolvedMethods);
    }

    private MethodDefinition resolveMethod(MethodDefinition method) {
        if (!method.isAbstract() || method.getParameters().size() != 1) {
            return method;
        }

        TypeMirror sourceTypeMirror = method.getParameters().get(0).getType();
        TypeMirror targetTypeMirror = method.getReturnType();

        TypeElement sourceType = asTypeElement(sourceTypeMirror);
        TypeElement targetType = asTypeElement(targetTypeMirror);

        if (sourceType == null || targetType == null) {
            return method;
        }

        Set<String> sourcePropertyNames = discoverPropertyNames(sourceType);
        Set<String> targetPropertyNames = discoverPropertyNames(targetType);

        Set<String> explicitTargets =
                method.getDirectives().stream().map(MapDirective::getTarget).collect(toSet());

        List<MapDirective> autoDirectives = targetPropertyNames.stream()
                .filter(sourcePropertyNames::contains)
                .filter(name -> !explicitTargets.contains(name))
                .map(name -> new MapDirective(name, name))
                .collect(toList());

        if (autoDirectives.isEmpty()) {
            return method;
        }

        List<MapDirective> allDirectives = new ArrayList<>(method.getDirectives());
        allDirectives.addAll(autoDirectives);
        return method.withDirectives(allDirectives);
    }

    private @Nullable TypeElement asTypeElement(TypeMirror typeMirror) {
        if (typeMirror instanceof DeclaredType) {
            return (TypeElement) ((DeclaredType) typeMirror).asElement();
        }
        return null;
    }

    private Set<String> discoverPropertyNames(TypeElement type) {
        return strategies.stream()
                .flatMap(strategy -> strategy.discoverProperties(type, processingEnv).stream())
                .map(Property::getName)
                .collect(toSet());
    }
}
