package io.github.joke.percolate.spi.builtins.test

import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class ResolveCtxBuilderSpec extends Specification {

    def 'default builder produces a HarnessResolveCtx-equivalent ctx'() {
        given:
        def ctx = new ResolveCtxBuilder().build()

        expect:
        ctx.types() == TypeUniverse.types()
        ctx.elements() == TypeUniverse.elements()
        ctx.callableMethods().producing(TypeUniverse.STRING).toList().empty
    }

    def 'builder withCallableMethods override is honoured'() {
        given:
        def fakeReceiver = Stub(io.github.joke.percolate.spi.Receiver)
        fakeReceiver.asExpression() >> null
        def fakeCandidate = new io.github.joke.percolate.spi.MethodCandidate(null, fakeReceiver)
        def mockMethods = Mock(io.github.joke.percolate.spi.CallableMethods) {
            producing(_) >> fakeCandidate.stream()
        }

        when:
        def ctx = new ResolveCtxBuilder().withCallableMethods(mockMethods).build()

        then:
        ctx.callableMethods() == mockMethods
        ctx.callableMethods().producing(TypeUniverse.STRING).toList().size() == 1
    }
}
