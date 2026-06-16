package io.github.joke.percolate.processor.stages.discover

import com.google.testing.compile.Compiler
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.graph.MethodScope
import io.github.joke.percolate.processor.model.MappingDirective
import spock.lang.Specification
import spock.lang.Tag

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement

/**
 * Discovery reads {@code source}/{@code constant}/{@code defaultValue} against the {@code Map.UNSET} sentinel via
 * annotation-mirror walking (with the annotation's declared defaults), so it is exercised under a real compiler
 * round rather than with hand-mocked mirrors.
 */
@Tag('integration')
class DiscoverMappingsStageSpec extends Specification {

    def 'a constant directive is discovered with a present constant and no source'() {
        when:
        def directives = discover('@Map(target = "status", constant = "ACTIVE")')

        then:
        directives.size() == 1
        def d = directives[0]
        d.hasConstant()
        d.constant == 'ACTIVE'
        !d.hasSource()
        d.source == null
        d.constantValue != null
        d.sourceValue == null
    }

    def 'a default directive is discovered alongside its source'() {
        when:
        def directives = discover('@Map(target = "name", source = "in.name", defaultValue = "unknown")')

        then:
        directives.size() == 1
        def d = directives[0]
        d.hasSource()
        d.source == 'in.name'
        d.hasDefaultValue()
        d.defaultValue == 'unknown'
        d.defaultValueValue != null
        !d.hasConstant()
    }

    def 'an empty-string constant is present, not absent'() {
        when:
        def directives = discover('@Map(target = "note", constant = "")')

        then:
        directives.size() == 1
        def d = directives[0]
        d.hasConstant()
        d.constant == ''
        d.constantValue != null
        !d.hasSource()
    }

    def 'discovery attaches a goal spec to each method scope carrying every directive'() {
        when: 'the discovery phase runs over a mapper with two directives (no seed stage)'
        def ctx = discoverContext()

        then: 'the goal spec is reachable by the method scope and carries every declared binding'
        def scope = new MethodScope(ctx.shape.abstractMethods[0])
        def goal = ctx.goalSpecs[scope]
        goal != null
        goal.declaredChildren('') == ['first', 'second'] as Set
        goal.bindingFor('first').present
        goal.bindingFor('second').present
    }

    private static MapperContext discoverContext() {
        def processor = new CapturingContextProcessor()
        def mapper = JavaFileObjects.forSourceLines(
                'test.M',
                'package test;',
                'import io.github.joke.percolate.Mapper;',
                'import io.github.joke.percolate.Map;',
                '@Mapper',
                'public interface M {',
                '    @Map(target = "first", source = "in.first")',
                '    @Map(target = "second", source = "in.second")',
                '    Object map(Object in);',
                '}')
        def compilation = Compiler.javac()
                .withProcessors(processor)
                .compile(mapper)
        assert compilation.errors().empty
        Objects.requireNonNull(processor.captured)
    }

    private static List<MappingDirective> discover(final String mapAnnotation) {
        def processor = new CapturingDiscoveryProcessor()
        def mapper = JavaFileObjects.forSourceLines(
                'test.M',
                'package test;',
                'import io.github.joke.percolate.Mapper;',
                'import io.github.joke.percolate.Map;',
                '@Mapper',
                'public interface M {',
                "    ${mapAnnotation}",
                '    Object map(Object in);',
                '}')
        def compilation = Compiler.javac()
                .withProcessors(processor)
                .compile(mapper)
        assert compilation.errors().empty
        List.copyOf(processor.captured)
    }
}

/** A throwaway processor that runs {@link DiscoverMappingsStage} over the {@code @Mapper} type's abstract methods. */
@SupportedAnnotationTypes('io.github.joke.percolate.Mapper')
@SupportedSourceVersion(SourceVersion.RELEASE_11)
class CapturingDiscoveryProcessor extends AbstractProcessor {

    final List<MappingDirective> captured = []

    @Override
    boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        def stage = new DiscoverMappingsStage(processingEnv.elementUtils)
        annotations.each { annotation ->
            roundEnv.getElementsAnnotatedWith(annotation).each { mapperType ->
                mapperType.enclosedElements
                        .findAll { it.kind == ElementKind.METHOD }
                        .each { method ->
                            captured.addAll(stage.extractDirectives((method as ExecutableElement).annotationMirrors))
                        }
            }
        }
        false
    }
}

/** A throwaway processor that runs the discovery stages and captures the resulting per-mapper context. */
@SupportedAnnotationTypes('io.github.joke.percolate.Mapper')
@SupportedSourceVersion(SourceVersion.RELEASE_11)
class CapturingContextProcessor extends AbstractProcessor {

    MapperContext captured

    @Override
    boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        def elements = processingEnv.elementUtils
        def discoverAbstract = new DiscoverAbstractMethodsStage(elements, processingEnv.typeUtils)
        def discoverMappings = new DiscoverMappingsStage(elements)
        annotations.each { annotation ->
            roundEnv.getElementsAnnotatedWith(annotation).each { mapperType ->
                def ctx = new MapperContext(mapperType as TypeElement)
                discoverAbstract.run(ctx)
                discoverMappings.run(ctx)
                captured = ctx
            }
        }
        false
    }
}
