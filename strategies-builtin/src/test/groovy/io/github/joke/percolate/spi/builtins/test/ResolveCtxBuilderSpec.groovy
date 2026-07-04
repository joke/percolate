package io.github.joke.percolate.spi.builtins.test

import io.github.joke.percolate.spi.test.PrivateTypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class ResolveCtxBuilderSpec extends Specification {

    @Shared PrivateTypeUniverse javac = new PrivateTypeUniverse()

    def 'a builder over a PrivateTypeUniverse produces a ctx backed by its types/elements'() {
        given:
        def ctx = new ResolveCtxBuilder(javac).build()

        expect:
        ctx.types() == javac.types()
        ctx.elements() == javac.elements()
        ctx.callableMethods().producing(javac.STRING).toList().empty
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
        def ctx = new ResolveCtxBuilder(javac).withCallableMethods(mockMethods).build()

        then:
        ctx.callableMethods() == mockMethods
        ctx.callableMethods().producing(javac.STRING).toList().size() == 1
    }
}
