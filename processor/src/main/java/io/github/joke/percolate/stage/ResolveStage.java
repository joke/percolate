package io.github.joke.percolate.stage;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import io.github.joke.percolate.di.RoundScoped;
import io.github.joke.percolate.model.MapDirective;
import io.github.joke.percolate.model.MapperDefinition;
import io.github.joke.percolate.model.MethodDefinition;
import io.github.joke.percolate.model.ParameterDefinition;
import io.github.joke.percolate.model.Property;
import io.github.joke.percolate.spi.PropertyDiscoveryStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    private static final String WILDCARD_SUFFIX = ".*";

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
        if (!method.isAbstract()) {
            return method;
        }

        // Phase 1: Expand wildcard directives
        List<MapDirective> directives = expandWildcards(method);

        // Phase 2: Same-name auto-matching for single-param methods without directives
        if (method.getParameters().size() == 1) {
            directives = addSameNameMatches(method, directives);
        }

        if (directives.equals(method.getDirectives())) {
            return method;
        }

        return method.withDirectives(directives);
    }

    private List<MapDirective> expandWildcards(MethodDefinition method) {
        List<MapDirective> original = method.getDirectives();

        boolean hasWildcards = original.stream().anyMatch(d -> d.getSource().endsWith(WILDCARD_SUFFIX));
        if (!hasWildcards) {
            return original;
        }

        Set<String> explicitTargets = original.stream()
                .filter(d -> !d.getSource().endsWith(WILDCARD_SUFFIX))
                .map(MapDirective::getTarget)
                .collect(toSet());

        List<MapDirective> result = new ArrayList<>();

        for (MapDirective directive : original) {
            if (!directive.getSource().endsWith(WILDCARD_SUFFIX)) {
                result.add(directive);
                continue;
            }

            String sourceBase =
                    directive.getSource().substring(0, directive.getSource().length() - WILDCARD_SUFFIX.length());
            String targetBase = directive.getTarget();

            @Nullable TypeElement sourceType = resolveSourceType(method, sourceBase);
            if (sourceType == null) {
                result.add(directive);
                continue;
            }

            Set<String> propertyNames = discoverPropertyNames(sourceType);

            List<MapDirective> expanded = propertyNames.stream()
                    .map(name -> {
                        String expandedTarget = ".".equals(targetBase) ? name : targetBase + "." + name;
                        String expandedSource = sourceBase + "." + name;
                        return new MapDirective(expandedTarget, expandedSource);
                    })
                    .filter(d -> !explicitTargets.contains(d.getTarget()))
                    .collect(toList());

            result.addAll(expanded);
        }

        return result;
    }

    private @Nullable TypeElement resolveSourceType(MethodDefinition method, String sourceBase) {
        @SuppressWarnings("StringSplitter")
        String[] segments = sourceBase.split("\\.");
        String paramName = segments[0];

        // Find the parameter by name
        Optional<ParameterDefinition> param = method.getParameters().stream()
                .filter(p -> p.getName().equals(paramName))
                .findFirst();

        if (!param.isPresent()) {
            return null;
        }

        @Nullable TypeElement current = asTypeElement(param.get().getType());

        // Walk remaining segments to resolve nested types
        for (int i = 1; i < segments.length && current != null; i++) {
            String segment = segments[i];
            @Nullable TypeElement finalCurrent = current;
            Optional<Property> prop = strategies.stream()
                    .flatMap(s -> s.discoverProperties(finalCurrent, processingEnv).stream())
                    .filter(p -> p.getName().equals(segment))
                    .findFirst();
            current = prop.map(p -> asTypeElement(p.getType())).orElse(null);
        }

        return current;
    }

    private List<MapDirective> addSameNameMatches(MethodDefinition method, List<MapDirective> directives) {
        TypeMirror sourceTypeMirror = method.getParameters().get(0).getType();
        TypeMirror targetTypeMirror = method.getReturnType();

        @Nullable TypeElement sourceType = asTypeElement(sourceTypeMirror);
        @Nullable TypeElement targetType = asTypeElement(targetTypeMirror);

        if (sourceType == null || targetType == null) {
            return directives;
        }

        Set<String> sourcePropertyNames = discoverPropertyNames(sourceType);
        Set<String> targetPropertyNames = discoverPropertyNames(targetType);

        Set<String> existingTargets =
                directives.stream().map(MapDirective::getTarget).collect(toSet());

        List<MapDirective> autoDirectives = targetPropertyNames.stream()
                .filter(sourcePropertyNames::contains)
                .filter(name -> !existingTargets.contains(name))
                .map(name -> new MapDirective(name, name))
                .collect(toList());

        if (autoDirectives.isEmpty()) {
            return directives;
        }

        List<MapDirective> allDirectives = new ArrayList<>(directives);
        allDirectives.addAll(autoDirectives);
        return allDirectives;
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
