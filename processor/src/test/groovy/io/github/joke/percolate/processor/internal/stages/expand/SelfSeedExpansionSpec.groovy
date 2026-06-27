package io.github.joke.percolate.processor.internal.stages.expand

import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.ProcessorModule
import io.github.joke.percolate.processor.internal.graph.MethodScope
import io.github.joke.percolate.processor.internal.graph.TargetLocation
import io.github.joke.percolate.processor.nullability.JspecifyNullabilityResolver
import io.github.joke.percolate.processor.nullability.NullabilityAnnotations
import io.github.joke.percolate.processor.internal.stages.discover.DiscoverAbstractMethodsStage
import io.github.joke.percolate.processor.internal.stages.discover.DiscoverCallableMethodsStage
import io.github.joke.percolate.processor.internal.stages.discover.DiscoverMappingsStage
import io.github.joke.percolate.spi.ExpansionStrategy
import io.github.joke.percolate.spi.SourceProjection
import spock.lang.Specification
import spock.lang.Tag

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement

/**
 * With {@code SeedStage} gone (design D4), {@code ExpandStage} creates an empty graph and self-seeds one return-type
 * demand per abstract method; the per-method goal spec is the discovery phase's product, available to expansion
 * without any seed stage.
 *
 * <p>This is an engine-only view: with just the {@code FakeStrategy} zero-port producer on the classpath, the engine
 * self-seeds and grows the graph. The demand-driven <em>descent</em> claims this spec used to make — a parameter
 * becomes a source {@code LEAF} only when an accessor chain bottoms out at it, and an unused parameter is never
 * materialised — need a producer that demands the target's children (assembly), which a zero-port sentinel cannot
 * provide. That white-box descent coverage is deferred to the engine-only descent fixture in
 * {@code audit-e2e-test-placement}; the black-box equivalent is already exercised by the builtin e2e specs.
 */
@Tag('integration')
class SelfSeedExpansionSpec extends Specification {

    def 'the graph self-seeds the return root from the discovery-owned goal spec, without a seed stage'() {
        given:
        def ctx = expand(
                '    @Map(target = "name", source = "src.name")',
                'Target map(Source src)')
        def scope = new MethodScope(ctx.shape.abstractMethods[0])
        def values = ctx.graph.valuesIn(scope).toList()

        expect: 'the goal spec is discovery-owned and reachable by the method scope'
        ctx.goalSpecs[scope] != null
        ctx.goalSpecs[scope].declaredChildren('') == ['name'] as Set

        and: 'the graph grew from empty: it carries the self-seeded return root and a produced operation'
        !values.empty
        values.any { it.loc instanceof TargetLocation && it.loc.returnRoot }
        ctx.graph.operations().count() > 0
    }

    private static MapperContext expand(final String mapAnnotation, final String mapMethod) {
        def source = JavaFileObjects.forSourceLines('test.Source',
                'package test;',
                'public final class Source {',
                '    private final String name;',
                '    public Source(String name) { this.name = name; }',
                '    public String getName() { return name; }',
                '}')
        def target = JavaFileObjects.forSourceLines('test.Target',
                'package test;',
                'public final class Target {',
                '    private final String name;',
                '    public Target(String name) { this.name = name; }',
                '    public String getName() { return name; }',
                '}')
        def mapper = JavaFileObjects.forSourceLines('test.M',
                'package test;',
                'import io.github.joke.percolate.Mapper;',
                'import io.github.joke.percolate.Map;',
                '@Mapper',
                'public interface M {',
                mapAnnotation,
                "    ${mapMethod};",
                '}')
        def processor = new ExpandingProcessor()
        def compilation = Compiler.javac()
                .withProcessors(processor)
                .compile(source, target, mapper)
        assert compilation.errors().empty
        Objects.requireNonNull(processor.captured, 'expansion did not run')
    }
}

/** A throwaway processor that runs discovery and the self-seeding {@link ExpandStage} and captures the context. */
@SupportedAnnotationTypes('io.github.joke.percolate.Mapper')
@SupportedSourceVersion(SourceVersion.RELEASE_11)
class ExpandingProcessor extends AbstractProcessor {

    MapperContext captured

    @Override
    boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        def elements = processingEnv.elementUtils
        def types = processingEnv.typeUtils
        def resolver = new JspecifyNullabilityResolver(NullabilityAnnotations.jspecifyDefaults(), elements)
        def strategies = ServiceLoader.load(ExpansionStrategy, ExpandingProcessor.classLoader)
                .collect { it }
                .sort(false) { [it.priority(), it.class.name] }
        def projections = ServiceLoader.load(SourceProjection, ExpandingProcessor.classLoader)
                .collect { it }
                .sort(false) { it.class.name }
        def expand = ProcessorModule.assembleExpansionPipeline(strategies, projections, types, elements, resolver)
        def discoverAbstract = new DiscoverAbstractMethodsStage(elements, types)
        def discoverMappings = new DiscoverMappingsStage(elements)
        def discoverCallable = new DiscoverCallableMethodsStage(elements, types)
        annotations.each { annotation ->
            roundEnv.getElementsAnnotatedWith(annotation).each { mapperType ->
                def ctx = new MapperContext(mapperType as TypeElement)
                discoverAbstract.run(ctx)
                discoverMappings.run(ctx)
                discoverCallable.run(ctx)
                expand.run(ctx)
                captured = ctx
            }
        }
        false
    }
}
