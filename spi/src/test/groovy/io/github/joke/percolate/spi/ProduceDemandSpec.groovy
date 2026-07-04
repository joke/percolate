package io.github.joke.percolate.spi

import io.github.joke.percolate.spi.test.TypeUniverse
import io.github.joke.percolate.spi.types.TypeRefs
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.Element
import javax.lang.model.type.TypeMirror

@Tag('unit')
class ProduceDemandSpec extends Specification {

    def 'targetTypeRef derives from targetType via TypeRefs.of'() {
        ProduceDemand demand = [
                targetType      : { TypeUniverse.STRING },
                targetNullness  : { Nullability.NON_NULL },
                directive       : { Optional.empty() },
                declaredChildren: { [] as Set },
                bindingName     : { '' },
                nullnessOf      : { TypeMirror t, Element s -> Nullability.NON_NULL },
        ] as ProduceDemand

        expect: 'the default method resolves even for a map-coerced instance that never mentions it'
        demand.targetTypeRef() == TypeRefs.of(TypeUniverse.STRING)
    }
}
