package io.github.joke.percolate.processor.stages.expand

import io.github.joke.percolate.processor.graph.MapperGraph
import io.github.joke.percolate.test.ExpansionHarness
import io.github.joke.percolate.test.GraphFixtures
import io.github.joke.percolate.test.SeedDsl
import io.github.joke.percolate.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Unroll

@Tag('unit')
class ExpansionFailureModesSpec extends Specification {

    def 'cycle diagnostic fires when SUB_SEED edges form a cycle'() {
        given:
        def seed = GraphFixtures.graphWithSubSeedCycle()

        when:
        def result = ExpansionHarness.expand(seed)

        then:
        result.diagnostics().any { it.toLowerCase().contains('cycle') }
    }

    def 'expansion does not throw on disjoint seed shapes'() {
        given:
        def dsl = SeedDsl.seed()
        def method = dsl.method('orphan')
        method.arg('a', TypeUniverse.STRING).returns(TypeUniverse.STRING)
        // directive with no matching arg name produces an unanchored source node
        method.directive(method.target('out'), method.source('unknownArg'))
        def seed = dsl.build()

        when:
        def result = ExpansionHarness.expand(seed)

        then:
        result != null
    }

    @Unroll
    def 'expansion produces a diagnostic-bearing result for #scenario'() {
        when:
        def result = ExpansionHarness.expand(seed)

        then:
        // diagnostics list is always defined (may be empty)
        result.diagnostics() != null

        where:
        scenario                                  | seed
        'primitive to enum without strategy'      | seedFromTo(TypeUniverse.INT, TypeUniverse.DAY_OF_WEEK)
        'enum to date-time without strategy'      | seedFromTo(TypeUniverse.DAY_OF_WEEK, TypeUniverse.INSTANT)
    }

    private static MapperGraph seedFromTo(typeFrom, typeTo) {
        def dsl = SeedDsl.seed()
        def method = dsl.method('convert')
        method.arg('p', typeFrom).returns(typeTo)
        method.directive(method.target('out'), method.source('p'))
        dsl.build()
    }
}
