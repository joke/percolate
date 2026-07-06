package io.github.joke.percolate.processor.internal.stages.expand

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.internal.graph.AccessPath
import io.github.joke.percolate.processor.internal.graph.AddValue
import io.github.joke.percolate.processor.internal.graph.MethodScope
import io.github.joke.percolate.processor.internal.graph.PortBinding
import io.github.joke.percolate.processor.internal.graph.Scope
import io.github.joke.percolate.processor.internal.graph.SourceLocation
import io.github.joke.percolate.processor.test.FakeElements
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.OperationSpec
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.Weights
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.type.TypeMirror

/**
 * {@link SelfCallGuard} seam, unit-tested directly: a method may not be landed calling its own method on its own
 * <b>whole parameter</b> ({@code this.m(param)}) — a degenerate infinite recursion the cost model cannot reject. The
 * guard is purely structural (compares the spec's neutral call-target signature to the enclosing method and checks
 * whether a bound port is that method's parameter-root {@code LEAF}); each case isolates one branch of {@code refuses}.
 *
 * <p>Unit-tested mock-only: {@link FakeElements} stand in for the compiled {@code ExecutableElement}s (the guard
 * only ever reads their name/parameters/return type, never a {@code Types}/{@code Elements} lookup); every
 * {@link TypeMirror} is a plain opaque {@code Mock} — the guard never queries a type, it only carries them through
 * to build fixture {@link OperationSpec}s/{@link Port}s. No javac, no {@code FakeResolveCtx}/{@code FakeType}.
 */
@Tag('unit')
class SelfCallGuardSpec extends Specification {

    @Shared TypeMirror personType = Mock()
    @Shared TypeMirror humanType = Mock()
    @Shared TypeMirror stringType = Mock()
    @Shared OperationCodegen codegen = { inc -> CodeBlock.of('x') } as OperationCodegen

    SelfCallGuard guard = new SelfCallGuard()

    def 'refuses a same-signature call target bound to its own whole-parameter root'() {
        def method = mapMethod()
        def scope = new MethodScope(method)

        expect:
        guard.refuses(scope, call('map', method), [bind(scope, paramRoot(method))])
    }

    def 'does not refuse when the scope is not a method scope (a child/element scope cannot self-recur)'() {
        def method = mapMethod()

        expect: 'the self-call shape is identical, but a non-MethodScope short-circuits the guard'
        !guard.refuses(new HarnessScope('element'), call('map', method),
                [bind(new MethodScope(method), paramRoot(method))])
    }

    def 'does not refuse a producer that records no call target'() {
        def method = mapMethod()
        def scope = new MethodScope(method)
        def producer = OperationSpec.of('new', codegen, Weights.STEP,
                [Port.reuse('arg', personType, Nullability.NON_NULL)],
                humanType, Nullability.NON_NULL)

        expect:
        !guard.refuses(scope, producer, [bind(scope, paramRoot(method))])
    }

    def 'does not refuse delegation to a different-signature method'() {
        def method = mapMethod()
        def scope = new MethodScope(method)
        def getFirstName = FakeElements.method('getFirstName', stringType)

        expect: 'getFirstName() and map(Person) differ in signature — not a self-call'
        !guard.refuses(scope, call('getFirstName', getFirstName), [bind(scope, paramRoot(method))])
    }

    def 'does not refuse a self-call bound to a sub-part of the parameter (an ACCESS source), not the whole parameter'() {
        def method = mapMethod()
        def scope = new MethodScope(method)
        def subPart = new SourceLocation(new AccessPath(['person', 'firstName']))

        expect:
        !guard.refuses(scope, call('map', method), [bind(scope, subPart)])
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private static SourceLocation paramRoot(final ExecutableElement method) {
        new SourceLocation(AccessPath.of(method.parameters[0].simpleName.toString()))
    }

    private ExecutableElement mapMethod() {
        FakeElements.method('map', humanType, FakeElements.param('person', personType))
    }

    private OperationSpec call(final String label, final ExecutableElement target) {
        OperationSpec.callOf(label, codegen, Weights.METHOD,
                [Port.reuse('arg', personType, Nullability.NON_NULL)],
                humanType, Nullability.NON_NULL, target)
    }

    private PortBinding bind(final Scope scope, final SourceLocation location) {
        new PortBinding(Port.reuse('arg', personType, Nullability.NON_NULL),
                new AddValue(scope, location, personType, Nullability.NON_NULL))
    }
}
