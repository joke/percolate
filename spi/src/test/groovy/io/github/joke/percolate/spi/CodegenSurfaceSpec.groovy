package io.github.joke.percolate.spi

import spock.lang.Specification
import spock.lang.Tag

/**
 * Pins the trimmed codegen surface (readable-graph-dumps): {@code OperationSpec} carries a typed {@code label},
 * {@code OperationCodegen.render} takes only {@code IncomingValues}, and the dead {@code VarNames} /
 * {@code LoopContainerCodegen} types no longer exist in the SPI package.
 */
@Tag('unit')
class CodegenSurfaceSpec extends Specification {

    def 'VarNames is not part of the SPI'() {
        when:
        Class.forName('io.github.joke.percolate.spi.VarNames')

        then:
        thrown(ClassNotFoundException)
    }

    def 'LoopContainerCodegen is not part of the SPI'() {
        when:
        Class.forName('io.github.joke.percolate.spi.LoopContainerCodegen')

        then:
        thrown(ClassNotFoundException)
    }

    def 'OperationCodegen.render takes only IncomingValues'() {
        given:
        def renders = OperationCodegen.declaredMethods.findAll { it.name == 'render' }

        expect:
        renders.size() == 1
        renders[0].parameterTypes.toList() == [IncomingValues]
    }

    def 'OperationSpec carries a label'() {
        expect:
        OperationSpec.getMethod('getLabel').returnType == String
    }
}
