package io.github.joke.percolate.processor.internal.stages.discover

import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.internal.graph.MethodScope
import io.github.joke.percolate.processor.model.MapperShape
import io.github.joke.percolate.processor.test.MappingDirectives
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement

/**
 * {@link DiscoverMappingsStage} glue, unit-tested mock-only: the stage threads each method through
 * {@link MapDirectiveReader} and {@link MapEnumDirectiveReader} (design D4 of change
 * {@code decouple-engine-from-strategy-semantics}) and installs the {@code MapperMappings} plus a
 * per-method-scope {@code GoalSpec} on the context. The collaborators are mocked; their own {@code javax.lang.model}
 * reading is covered by the compile-based feature-e2e layer — no javac substrate here.
 */
@Tag('unit')
class DiscoverMappingsStageSpec extends Specification {

    MapDirectiveReader mapReader = Mock()
    MapEnumDirectiveReader mapEnumReader = Mock()
    DiscoverMappingsStage stage = new DiscoverMappingsStage(mapReader, mapEnumReader)

    def 'toMethodMappings threads the method through the map reader'() {
        ExecutableElement method = Mock()
        def first = MappingDirectives.of('first')

        when:
        def result = stage.toMethodMappings(method)

        then:
        1 * mapReader.extractDirectives(method) >> [first]
        0 * _

        expect:
        result.method.is(method)
        result.directives == [first]
    }

    def 'run installs the mappings and a per-method-scope goal spec carrying the declared binding'() {
        TypeElement mapperType = Mock()
        ExecutableElement method = Mock()
        def ctx = new MapperContext(mapperType)
        ctx.shape = new MapperShape(mapperType, [method])

        when:
        stage.run(ctx)

        then:
        1 * mapReader.extractDirectives(method) >> [MappingDirectives.of('first')]
        1 * mapEnumReader.extractOverrides(method) >> []
        0 * _

        expect: 'the goal spec is reachable by the method scope and declares the child'
        ctx.mappings != null
        ctx.mappings.type.is(mapperType)
        def goal = ctx.goalSpecs[new MethodScope(method)]
        goal != null
        goal.declaredChildren('') == ['first'] as Set
        goal.bindingFor('first').present
        goal.enumOverrides == []
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
}
