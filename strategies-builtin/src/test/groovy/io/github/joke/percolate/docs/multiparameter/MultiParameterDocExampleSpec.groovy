package io.github.joke.percolate.docs.multiparameter

import spock.lang.Specification
import spock.lang.Tag

/**
 * Backs the manual's multi-parameter mapper method section. Each mapper is real source compiled by the
 * ordinary {@code compileTestJava} task through the real starter — no compile-testing. Covers two
 * differently-typed parameters, two same-typed parameters distinguished by name, and a sub-target assembled
 * from both parameter roots.
 */
@Tag('integration')
class MultiParameterDocExampleSpec extends Specification {

    def 'two differently-typed parameters each feed their own target'() {
        def mapper = new TwoParamMapperImpl()

        expect:
        verifyAll(mapper.map(new Customer('Ada'), new Address('Baker Street'))) {
            customerName == 'Ada'
            street == 'Baker Street'
        }
    }

    def 'two same-typed parameters are distinguished by name'() {
        def mapper = new SameTypeMapperImpl()

        expect:
        verifyAll(mapper.compare(new Person('Ada'), new Person('Grace'))) {
            oldName == 'Ada'
            newName == 'Grace'
        }
    }

    def 'a sub-target is assembled from both parameter roots'() {
        def mapper = new SubTargetMapperImpl()

        expect:
        verifyAll(mapper.map(new Customer('Ada'), new Address('Baker Street')).summary) {
            customerName == 'Ada'
            street == 'Baker Street'
        }
    }
}
