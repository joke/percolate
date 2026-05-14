package io.github.joke.percolate.processor.stages.expand

import io.github.joke.percolate.processor.graph.MapperGraph
import io.github.joke.percolate.test.ExpansionHarness
import io.github.joke.percolate.test.SeedDsl
import io.github.joke.percolate.test.TypeUniverse
import javax.lang.model.type.TypeMirror
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Unroll

@Tag('unit')
class ExpansionCapabilitiesSpec extends Specification {

    @Unroll
    def 'expansion completes via SPI mode for #scenario'() {
        when:
        def result = ExpansionHarness.expand(seed)

        then:
        result != null
        result.expandedGraph() != null
        result.converged()

        where:
        scenario               | seed
        'identity String'      | identitySeed(TypeUniverse.STRING)
        'identity Integer'     | identitySeed(TypeUniverse.INTEGER)
        'identity LocalDate'   | identitySeed(TypeUniverse.LOCAL_DATE_TIME)
        'identity DayOfWeek'   | identitySeed(TypeUniverse.DAY_OF_WEEK)
    }

    @Unroll
    def 'expansion is idempotent for #scenario'() {
        given:
        def first = ExpansionHarness.expand(seed)

        when:
        def second = ExpansionHarness.expand(first.expandedGraph())

        then:
        second.expandedGraph().nodeCount() == first.expandedGraph().nodeCount()
        second.expandedGraph().edgeCount() == first.expandedGraph().edgeCount()

        where:
        scenario           | seed
        'identity String'  | identitySeed(TypeUniverse.STRING)
        'identity Integer' | identitySeed(TypeUniverse.INTEGER)
    }

    private static MapperGraph identitySeed(TypeMirror type) {
        def dsl = SeedDsl.seed()
        def method = dsl.method('map')
        method.arg('p', type).returns(type)
        method.directive(method.target('out'), method.source('p'))
        dsl.build()
    }
}
