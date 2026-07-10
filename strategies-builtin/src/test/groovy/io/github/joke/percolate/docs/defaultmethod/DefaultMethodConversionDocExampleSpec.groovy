package io.github.joke.percolate.docs.defaultmethod

import spock.lang.Specification
import spock.lang.Tag

/**
 * Backs the manual's default-method-conversions page. {@code ProductMapper} is real source compiled by the
 * ordinary {@code compileTestJava} task through the real starter — no compile-testing. {@code formatPrice} is
 * left exactly as written; percolate calls it wherever it needs a {@code String} produced from a {@code long}.
 */
@Tag('integration')
class DefaultMethodConversionDocExampleSpec extends Specification {

    def 'map calls the hand-written default method to format the price'() {
        def mapper = new ProductMapperImpl()

        expect:
        mapper.map(new Product(1099L)).price == '$10.99'
    }
}
