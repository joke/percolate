package io.github.joke.percolate.spi

import spock.lang.Specification
import spock.lang.Tag

/**
 * Guards the candidate-free, uniform target-driven SPI surface (change {@code target-driven-engine} §§ 3.2/3.5): the
 * {@code spi} package exposes no candidate-iterating mixin and the {@link Demand} producer contract carries no
 * source-candidate snapshot. (Container-kind delegation to the {@code ResolveCtx} type-query seam is covered by
 * {@link ContainersSpec}.)
 */
@Tag('unit')
class CandidateFreeSurfaceSpec extends Specification {

    def 'the spi package exposes no CombinatorialMatch mixin'() {
        when:
        Class.forName('io.github.joke.percolate.spi.CombinatorialMatch')

        then:
        thrown(ClassNotFoundException)
    }

    def 'the spi package exposes no Candidate snapshot type'() {
        when:
        Class.forName('io.github.joke.percolate.spi.Candidate')

        then:
        thrown(ClassNotFoundException)
    }

    def 'the Demand producer contract exposes no candidates() accessor'() {
        expect:
        ProduceDemand.methods.every { it.name != 'candidates' }
    }
}
