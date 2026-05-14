package io.github.joke.percolate.test;

import io.github.joke.percolate.processor.spi.Bridge;
import io.github.joke.percolate.processor.spi.GroupTarget;
import io.github.joke.percolate.processor.spi.SourceStep;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import javax.lang.model.type.TypeMirror;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.PropertyDefaults;
import net.jqwik.api.Provide;

@PropertyDefaults(tries = 500)
public class PropertyTestBase {

    private static final List<String> METHOD_NAMES = List.of("map", "convert", "transform");

    private static final List<String> ARG_NAMES = List.of("a", "b", "c", "src", "input");

    private static final List<String> TARGET_PATHS = List.of("out", "result", "value", "destination");

    private static final List<Bridge> ALL_BRIDGES = loadAll(Bridge.class);

    private static final List<SourceStep> ALL_SOURCE_STEPS = loadAll(SourceStep.class);

    private static final List<GroupTarget> ALL_GROUP_TARGETS = loadAll(GroupTarget.class);

    protected PropertyTestBase() {}

    @Provide
    public Arbitrary<MapperSpec> mapperSpecs() {
        return methodSpecs().list().ofMinSize(1).ofMaxSize(3).map(MapperSpec::new);
    }

    @Provide
    public Arbitrary<StrategyBundle> strategyBundles() {
        return Combinators.combine(subsetOf(ALL_BRIDGES), subsetOf(ALL_SOURCE_STEPS), subsetOf(ALL_GROUP_TARGETS))
                .as(StrategyBundle::new);
    }

    private Arbitrary<MapperSpec.MethodSpec> methodSpecs() {
        final var name = Arbitraries.of(METHOD_NAMES);
        final var args = argSpecs().list().ofMinSize(1).ofMaxSize(3).uniqueElements(MapperSpec.ArgSpec::getName);
        final var returnType = typeArb();
        return Combinators.combine(name, args, returnType).flatAs(this::buildMethodSpec);
    }

    private Arbitrary<MapperSpec.MethodSpec> buildMethodSpec(
            final String name, final List<MapperSpec.ArgSpec> args, final TypeMirror returnType) {
        return directivesFor(args)
                .list()
                .ofMinSize(0)
                .ofMaxSize(3)
                .map(directives -> new MapperSpec.MethodSpec(name, args, returnType, directives));
    }

    private Arbitrary<MapperSpec.ArgSpec> argSpecs() {
        return Combinators.combine(Arbitraries.of(ARG_NAMES), typeArb()).as(MapperSpec.ArgSpec::new);
    }

    private Arbitrary<MapperSpec.DirectiveSpec> directivesFor(final List<MapperSpec.ArgSpec> args) {
        final var argNames = args.stream().map(MapperSpec.ArgSpec::getName).collect(Collectors.toList());
        final var sourcePath = Arbitraries.of(argNames);
        final var targetPath = Arbitraries.of(TARGET_PATHS);
        return Combinators.combine(targetPath, sourcePath).as(MapperSpec.DirectiveSpec::new);
    }

    private Arbitrary<TypeMirror> typeArb() {
        return Arbitraries.of(TypeUniverse.pool());
    }

    private static <T> Arbitrary<List<T>> subsetOf(final List<T> all) {
        if (all.isEmpty()) {
            return Arbitraries.just(List.of());
        }
        return Arbitraries.of(all).list().ofMinSize(0).ofMaxSize(all.size()).uniqueElements();
    }

    private static <T> List<T> loadAll(final Class<T> service) {
        return ServiceLoader.load(service, PropertyTestBase.class.getClassLoader()).stream()
                .map(ServiceLoader.Provider::get)
                .sorted(Comparator.comparing(t -> t.getClass().getName()))
                .collect(Collectors.toUnmodifiableList());
    }
}
