package io.github.joke.percolate.processor.internal.stages.expand

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.internal.graph.AccessPath
import io.github.joke.percolate.processor.internal.graph.AddValue
import io.github.joke.percolate.processor.internal.graph.MethodScope
import io.github.joke.percolate.processor.internal.graph.PortBinding
import io.github.joke.percolate.processor.internal.graph.Scope
import io.github.joke.percolate.processor.internal.graph.SourceLocation
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.processor.test.fixtures.Human
import io.github.joke.percolate.processor.test.fixtures.Person
import io.github.joke.percolate.processor.test.fixtures.PersonMapper
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.OperationSpec
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Isolated
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement

/**
 * {@link SelfCallGuard} seam, unit-tested directly: a method may not be landed calling its own method on its own
 * <b>whole parameter</b> ({@code this.m(param)}) — a degenerate infinite recursion the cost model cannot reject. The
 * guard is purely structural (compares the spec's neutral call-target signature to the enclosing method and checks
 * whether a bound port is that method's parameter-root {@code LEAF}); each case isolates one branch of {@code refuses}.
 */
@Tag('unit')
@Isolated // shares the static TypeUniverse javac; must not run concurrently with other fixture specs (race → flaky pitest)
class SelfCallGuardSpec extends Specification {

    @Shared TypeElement personType = TypeUniverse.of(Person)
    @Shared TypeElement humanType = TypeUniverse.of(Human)
    @Shared TypeElement personMapperType = TypeUniverse.of(PersonMapper)
    @Shared OperationCodegen codegen = { inc -> CodeBlock.of('x') } as OperationCodegen

    SelfCallGuard guard = new SelfCallGuard()

    def 'refuses a same-signature call target bound to its own whole-parameter root'() {
        given:
        def method = methodNamed(PersonMapper, 'map')
        def scope = new MethodScope(method)

        expect:
        guard.refuses(scope, call('map', method), [bind(scope, paramRoot(method))])
    }

    def 'does not refuse when the scope is not a method scope (a child/element scope cannot self-recur)'() {
        given:
        def method = methodNamed(PersonMapper, 'map')

        expect: 'the self-call shape is identical, but a non-MethodScope short-circuits the guard'
        !guard.refuses(new HarnessScope('element'), call('map', method),
                [bind(new MethodScope(method), paramRoot(method))])
    }

    def 'does not refuse a producer that records no call target'() {
        given:
        def method = methodNamed(PersonMapper, 'map')
        def scope = new MethodScope(method)
        def producer = OperationSpec.of('new', codegen, Weights.STEP,
                [Port.reuse('arg', personType.asType(), Nullability.NON_NULL)],
                humanType.asType(), Nullability.NON_NULL)

        expect:
        !guard.refuses(scope, producer, [bind(scope, paramRoot(method))])
    }

    def 'does not refuse delegation to a different-signature method'() {
        given:
        def method = methodNamed(PersonMapper, 'map')
        def scope = new MethodScope(method)

        expect: 'getFirstName() and map(Person) differ in signature — not a self-call'
        !guard.refuses(scope, call('getFirstName', methodNamed(Person, 'getFirstName')), [bind(scope, paramRoot(method))])
    }

    def 'does not refuse a self-call bound to a sub-part of the parameter (an ACCESS source), not the whole parameter'() {
        given:
        def method = methodNamed(PersonMapper, 'map')
        def scope = new MethodScope(method)
        def subPart = new SourceLocation(new AccessPath([method.parameters[0].simpleName.toString(), 'firstName']))

        expect:
        !guard.refuses(scope, call('map', method), [bind(scope, subPart)])
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private OperationSpec call(final String label, final ExecutableElement target) {
        OperationSpec.callOf(label, codegen, Weights.METHOD,
                [Port.reuse('arg', personType.asType(), Nullability.NON_NULL)],
                humanType.asType(), Nullability.NON_NULL, target)
    }

    private SourceLocation paramRoot(final ExecutableElement method) {
        new SourceLocation(AccessPath.of(method.parameters[0].simpleName.toString()))
    }

    private PortBinding bind(final Scope scope, final SourceLocation location) {
        new PortBinding(Port.reuse('arg', personType.asType(), Nullability.NON_NULL),
                new AddValue(scope, location, personType.asType(), Nullability.NON_NULL))
    }

    private ExecutableElement methodNamed(final Class<?> type, final String name) {
        TypeUniverse.of(type).enclosedElements.find {
            it.kind == ElementKind.METHOD && it.simpleName.toString() == name
        } as ExecutableElement
    }
}
