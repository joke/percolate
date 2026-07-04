package io.github.joke.percolate.spi

import io.github.joke.percolate.spi.test.TypeUniverse
import io.github.joke.percolate.spi.types.TypeRefs
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Element
import javax.lang.model.type.TypeMirror

@Tag('unit')
class DescendDemandSpec extends Specification {

    def 'parentTypeRef derives from parentType via TypeRefs.of'() {
        DescendDemand demand = [
                parentType    : { TypeUniverse.LIST_OF_STRING },
                parentNullness: { Nullability.NON_NULL },
                segment       : { 'street' },
                nullnessOf    : { TypeMirror t, Element s -> Nullability.NON_NULL },
        ] as DescendDemand

        expect: 'the default method resolves even for a map-coerced instance that never mentions it'
        demand.parentTypeRef() == TypeRefs.of(TypeUniverse.LIST_OF_STRING)
    }
}
