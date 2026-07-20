package io.github.joke.percolate.spi

import spock.lang.Specification
import spock.lang.Tag

/**
 * {@link ExpansionStrategy}'s default methods: {@code expand}/{@code descend} default to {@link Stream#empty()} so a
 * producer needs not implement {@code descend} and vice versa, and {@code priority} defaults to {@code 0}.
 */
@Tag('unit')
class ExpansionStrategySpec extends Specification {

    ExpansionStrategy strategy = new ExpansionStrategy() {}

    def 'expand defaults to an empty stream, touching neither the demand nor the seam'() {
        ProduceDemand demand = Mock()
        ResolveCtx ctx = Mock()

        when:
        def result = strategy.expand(demand, ctx)

        then:
        0 * _

        expect:
        result.toList() == []
    }

    def 'descend defaults to an empty stream, touching neither the demand nor the seam'() {
        DescendDemand demand = Mock()
        ResolveCtx ctx = Mock()

        when:
        def result = strategy.descend(demand, ctx)

        then:
        0 * _

        expect:
        result.toList() == []
    }

    def 'priority defaults to zero'() {
        expect:
        strategy.priority() == 0
    }
}
