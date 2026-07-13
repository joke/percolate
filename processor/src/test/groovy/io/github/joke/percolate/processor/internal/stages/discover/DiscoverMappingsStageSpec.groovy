package io.github.joke.percolate.processor.internal.stages.discover

import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.internal.graph.MethodScope
import io.github.joke.percolate.processor.model.MapperShape
import io.github.joke.percolate.processor.model.MappingDirective
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement

/**
 * {@link DiscoverMappingsStage} glue, unit-tested mock-only: the stage threads a method's mirrors through the
 * {@link AnnotationDirectiveReader}, maps each {@link RawDirective} through the {@link MappingDirectiveBuilder}, and
 * installs the {@code MapperMappings} plus a per-method-scope {@code GoalSpec} on the context. The collaborators are
 * mocked; every {@code AnnotationMirror}/{@code ExecutableElement}/{@code RawDirective} is an opaque, never-stubbed
 * token. The reader's own javac reading and the builder's presence logic are covered by their own specs and the
 * compile-based feature-e2e layer — no javac substrate here.
 */
@Tag('unit')
class DiscoverMappingsStageSpec extends Specification {

    AnnotationDirectiveReader reader = Mock()
    MappingDirectiveBuilder builder = Mock()
    DiscoverMappingsStage stage = new DiscoverMappingsStage(reader, builder)

    def 'extractDirectives threads the mirrors through the reader and maps each raw directive through the builder'() {
        AnnotationMirror mirror = Mock()
        List<AnnotationMirror> mirrors = [mirror]
        RawDirective rawA = Mock()
        RawDirective rawB = Mock()
        def first = directive('first')
        def second = directive('second')

        when:
        def result = stage.extractDirectives(mirrors)

        then:
        1 * reader.extractRawDirectives(mirrors) >> [rawA, rawB]
        1 * builder.toDirective(rawA) >> first
        1 * builder.toDirective(rawB) >> second
        0 * _

        expect:
        result == [first, second]
    }

    def 'run installs the mappings and a per-method-scope goal spec carrying the declared binding'() {
        TypeElement mapperType = Mock()
        ExecutableElement method = Mock()
        AnnotationMirror mirror = Mock()
        List<AnnotationMirror> mirrors = [mirror]
        RawDirective raw = Mock()
        def ctx = new MapperContext(mapperType)
        ctx.shape = new MapperShape(mapperType, [method])

        when:
        stage.run(ctx)

        then:
        1 * method.annotationMirrors >> mirrors
        1 * reader.extractRawDirectives(mirrors) >> [raw]
        1 * builder.toDirective(raw) >> directive('first')
        0 * _

        expect: 'the goal spec is reachable by the method scope and declares the child'
        ctx.mappings != null
        ctx.mappings.type.is(mapperType)
        def goal = ctx.goalSpecs[new MethodScope(method)]
        goal != null
        goal.declaredChildren('') == ['first'] as Set
        goal.bindingFor('first').present
    }

    def 'run is a no-op when discovery produced no shape'() {
        TypeElement mapperType = Mock()
        def ctx = new MapperContext(mapperType)

        when:
        stage.run(ctx)

        then:
        0 * _

        expect:
        ctx.mappings == null
    }

    private MappingDirective directive(final String target) {
        AnnotationMirror mirror = Mock()
        AnnotationValue targetValue = Mock()
        new MappingDirective(target, null, null, null, null, null,
                mirror, targetValue, null, null, null, null, null)
    }
}
