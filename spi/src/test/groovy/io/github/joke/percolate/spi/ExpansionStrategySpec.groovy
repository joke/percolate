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

    def 'expand defaults to an empty stream'() {
        expect:
        strategy.expand(Mock(ProduceDemand), Mock(ResolveCtx)).toList().empty
    }

    def 'descend defaults to an empty stream'() {
        expect:
        strategy.descend(Mock(DescendDemand), Mock(ResolveCtx)).toList().empty
    }

    def 'priority defaults to zero'() {
        expect:
        strategy.priority() == 0
    }
}
