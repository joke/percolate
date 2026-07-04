package io.github.joke.percolate.spi

import io.github.joke.percolate.spi.test.PrivateTypeUniverse
import io.github.joke.percolate.spi.types.TypeRefs
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement

@Tag('unit')
class OperationSpecSpec extends Specification {

    @Shared PrivateTypeUniverse javac = new PrivateTypeUniverse()

    Codegen codegen = Mock()

    def 'of derives outputTypeRef from outputType'() {
        expect:
        OperationSpec.of('label', codegen, 1, [], javac.STRING, Nullability.NON_NULL).outputTypeRef ==
                TypeRefs.of(javac.STRING)
    }

    def 'ofPartial derives outputTypeRef from outputType'() {
        expect:
        OperationSpec.ofPartial('label', codegen, 1, [], javac.STRING, Nullability.NON_NULL).outputTypeRef ==
                TypeRefs.of(javac.STRING)
    }

    def 'callOf derives outputTypeRef from outputType'() {
        ExecutableElement callTarget = Mock()

        expect:
        OperationSpec.callOf('label', codegen, 1, [], javac.STRING, Nullability.NON_NULL, callTarget)
                .outputTypeRef == TypeRefs.of(javac.STRING)
    }

    def 'mapping derives outputTypeRef from outputType'() {
        def childScope = new ChildScopeSpec(javac.STRING, Nullability.NON_NULL, javac.INTEGER, Nullability.NON_NULL)

        expect:
        OperationSpec.mapping('label', codegen, 1, [], javac.LIST_OF_STRING, Nullability.NON_NULL, childScope)
                .outputTypeRef == TypeRefs.of(javac.LIST_OF_STRING)
    }
}
