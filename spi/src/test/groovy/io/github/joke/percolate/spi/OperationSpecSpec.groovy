package io.github.joke.percolate.spi

import io.github.joke.percolate.spi.test.TypeUniverse
import io.github.joke.percolate.spi.types.TypeRefs
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement

@Tag('unit')
class OperationSpecSpec extends Specification {

    Codegen codegen = Mock()

    def 'of derives outputTypeRef from outputType'() {
        expect:
        OperationSpec.of('label', codegen, 1, [], TypeUniverse.STRING, Nullability.NON_NULL).outputTypeRef ==
                TypeRefs.of(TypeUniverse.STRING)
    }

    def 'ofPartial derives outputTypeRef from outputType'() {
        expect:
        OperationSpec.ofPartial('label', codegen, 1, [], TypeUniverse.STRING, Nullability.NON_NULL).outputTypeRef ==
                TypeRefs.of(TypeUniverse.STRING)
    }

    def 'callOf derives outputTypeRef from outputType'() {
        ExecutableElement callTarget = Mock()

        expect:
        OperationSpec.callOf('label', codegen, 1, [], TypeUniverse.STRING, Nullability.NON_NULL, callTarget)
                .outputTypeRef == TypeRefs.of(TypeUniverse.STRING)
    }

    def 'mapping derives outputTypeRef from outputType'() {
        def childScope = new ChildScopeSpec(TypeUniverse.STRING, Nullability.NON_NULL, TypeUniverse.INTEGER, Nullability.NON_NULL)

        expect:
        OperationSpec.mapping('label', codegen, 1, [], TypeUniverse.LIST_OF_STRING, Nullability.NON_NULL, childScope)
                .outputTypeRef == TypeRefs.of(TypeUniverse.LIST_OF_STRING)
    }
}
