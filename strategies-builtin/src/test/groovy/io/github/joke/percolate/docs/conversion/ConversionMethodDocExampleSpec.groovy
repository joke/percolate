package io.github.joke.percolate.docs.conversion

import spock.lang.Specification
import spock.lang.Tag

/**
 * Backs the manual's conversion-methods page. {@code CustomerMapper} is real source compiled by the ordinary
 * {@code compileTestJava} task through the real starter — no compile-testing. {@code map} needs an
 * {@code AddressView} for the nested {@code address} field, and reuses the sibling {@code toView} method as the
 * conversion rather than inlining the assembly.
 */
@Tag('integration')
class ConversionMethodDocExampleSpec extends Specification {

    def 'map reuses the sibling conversion method for the nested address field'() {
        def mapper = new CustomerMapperImpl()

        expect:
        mapper.map(new Customer(new Address('Elm Street'))).address.street == 'Elm Street'
    }

    def 'the conversion method is independently callable and generated'() {
        def mapper = new CustomerMapperImpl()

        expect:
        mapper.toView(new Address('Oak Street')).street == 'Oak Street'
    }
}
