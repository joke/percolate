package io.github.joke.percolate.processor.stages.seed

import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.graph.MethodScope
import io.github.joke.percolate.processor.graph.SourceLocation
import io.github.joke.percolate.processor.model.MapperMappings
import io.github.joke.percolate.processor.model.MappingDirective
import io.github.joke.percolate.processor.model.MethodMappings
import io.github.joke.percolate.processor.nullability.NullabilityResolver
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement

@Tag('unit')
class SeedStageSpec extends Specification {

    static final String MAPPER = 'io.github.joke.percolate.processor.test.fixtures.PersonMapper'

    final NullabilityResolver resolver = { type, scope -> Nullability.NON_NULL } as NullabilityResolver
    final SeedStage seedStage = new SeedStage(resolver)

    def 'seeding creates parameter and return roots and no edges or operations'() {
        given:
        def ctx = seed([directive('firstName', 'person.firstName')])
        def scope = new MethodScope(mapMethod())
        def graph = ctx.graph

        expect: 'one parameter-root Value (a single-segment source) and one return-root Value, both typed'
        def values = graph.valuesIn(scope).toList()
        values.findAll { it.loc instanceof SourceLocation && it.loc.path.segments.size() == 1 }.size() == 1
        values.find { it.loc.returnRoot } != null
        values.every { it.type.present && it.nullness.present }

        and: 'no producer structure is minted at seed time'
        graph.operations().count() == 0
        graph.deps().count() == 0
    }

    def 'a goal spec is attached to the method scope and carries every directive'() {
        given:
        def ctx = seed([directive('firstName', 'person.firstName'), directive('lastName', 'person.lastName')])
        def scope = new MethodScope(mapMethod())

        when:
        def goal = ctx.goalSpecs[scope]

        then:
        goal != null
        goal.declaredChildren('') == ['firstName', 'lastName'] as Set
        goal.bindingFor('firstName').present
        goal.bindingFor('lastName').present
    }

    private static ExecutableElement mapMethod() {
        TypeUniverse.element(MAPPER).enclosedElements.stream()
                .filter { it instanceof ExecutableElement && it.simpleName.toString() == 'map' }
                .map { it as ExecutableElement }
                .findFirst()
                .orElseThrow()
    }

    private static MappingDirective directive(final String target, final String source) {
        new MappingDirective(target, source, null, null, null, null, null, null, null)
    }

    private MapperContext seed(final List<MappingDirective> directives) {
        def type = TypeUniverse.element(MAPPER)
        def ctx = new MapperContext(type)
        ctx.mappings = new MapperMappings(type, [new MethodMappings(mapMethod(), directives)])
        seedStage.run(ctx)
        ctx
    }
}
