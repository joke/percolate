package io.github.joke.percolate.processor

import io.github.joke.percolate.spi.ResolveCtx
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class ResolveCtxSurfaceSpec extends Specification {

    def 'ResolveCtx exposes only types, elements, callableMethods'() {
        when:
        def methodNames = ResolveCtx.methods*.name as Set

        then:
        methodNames.containsAll(['types', 'elements', 'callableMethods'])
        !methodNames.contains('mapperType')
        !methodNames.contains('currentMethod')
    }

    def 'ProcessorModule holds no ThreadLocal field'() {
        expect:
        ProcessorModule.declaredFields.every { !ThreadLocal.isAssignableFrom(it.type) }
    }
}
