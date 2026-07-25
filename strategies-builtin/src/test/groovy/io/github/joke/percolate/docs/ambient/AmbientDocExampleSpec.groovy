package io.github.joke.percolate.docs.ambient

import spock.lang.Specification
import spock.lang.Tag

/**
 * Backs the manual's {@code @Ambient} page. Each mapper is real source compiled by the ordinary
 * {@code compileTestJava} task through the real starter — no compile-testing. Covers a top-level ambient
 * provider consumed by a default conversion method with a second argument, ambient-as-source, explicit-key
 * renaming, and an ambient consumed inside a container/element lambda.
 */
@Tag('integration')
class AmbientDocExampleSpec extends Specification {

    def 'a default conversion method consumes the ambient alongside its mapped argument'() {
        def mapper = new AmbientMapperImpl()
        def order = new Order('ORD-1')
        def customer = new Customer(new CustomerAddress('Baker Street'))

        expect:
        verifyAll(mapper.map(customer, order)) {
            orderRef == 'ORD-1'
            address.street == 'Baker Street'
            address.orderRef == 'ORD-1'
        }
    }

    def 'an explicit key lets a consumer rename its own parameter while binding the same ambient'() {
        def mapper = new RenamedKeyMapperImpl()

        expect:
        mapper.map(new Person('Ada'), new Locator('51N')).name == 'Ada@51N'
    }

    def 'an ambient is inherited into an element lambda for a container conversion'() {
        def mapper = new AmbientLambdaMapperImpl()
        def order = new CustomerOrder([new Item('sku-1'), new Item('sku-2')])
        def store = new Store('Central')

        expect:
        def lines = mapper.map(order, store).lines
        lines*.sku == ['sku-1', 'sku-2']
        lines*.storeName == ['Central', 'Central']
    }
}
