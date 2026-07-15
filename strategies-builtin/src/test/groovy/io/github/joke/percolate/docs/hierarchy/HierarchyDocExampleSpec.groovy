package io.github.joke.percolate.docs.hierarchy

import spock.lang.Specification
import spock.lang.Tag

/**
 * Backs the manual's mapper-structure "Hierarchies" section. {@code InvoiceMapper} is real source compiled
 * by the ordinary {@code compileTestJava} task through the real starter — no compile-testing. It is the
 * first e2e proof (outside {@code AbstractMethodReader}'s own use of
 * {@code MoreElements.getLocalAndInheritedMethods}) that an abstract method inherited from an unannotated
 * super-interface is discovered and generated exactly like one declared on the {@code @Mapper} type itself.
 */
@Tag('integration')
class HierarchyDocExampleSpec extends Specification {

    def 'the generated mapper implements both its own abstract method and the inherited one'() {
        def mapper = new InvoiceMapperImpl()

        expect:
        mapper.map(new Invoice(1099L)).total == 1099L
        mapper.mapLineItem(new LineItem('SKU-1')).sku == 'SKU-1'
    }

    def 'the default method and the concrete class method are left alone, still callable directly'() {
        def mapper = new InvoiceMapperImpl()

        expect:
        mapper.reference(42L) == 'INV-42'
        mapper.normalizeId('42') == 42L
    }
}
