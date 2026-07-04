package io.github.joke.percolate.processor.internal.stages.generate

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.internal.graph.AccessPath
import io.github.joke.percolate.processor.internal.graph.AddOperation
import io.github.joke.percolate.processor.internal.graph.AddValue
import io.github.joke.percolate.processor.internal.graph.ExtractedPlan
import io.github.joke.percolate.processor.internal.graph.MapperGraph
import io.github.joke.percolate.processor.internal.graph.MethodScope
import io.github.joke.percolate.processor.internal.graph.PortBinding
import io.github.joke.percolate.processor.internal.graph.SourceLocation
import io.github.joke.percolate.processor.internal.graph.TargetLocation
import io.github.joke.percolate.processor.internal.graph.TargetPath
import io.github.joke.percolate.processor.internal.graph.Value
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.test.PrivateTypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name

/**
 * {@link HoistPlan} seam, unit-tested directly: the pure hoist decision over an {@link ExtractedPlan} — a Value is
 * hoisted only when it has a chosen producer AND either feeds an n-ary assembly port or is consumed by more than one
 * in-plan port. Name allocation (slot name, empty-slot fallback, lambda type name) is exercised on the resulting plan.
 */
@Tag('unit')
class HoistPlanSpec extends Specification {

    static final OperationCodegen OP = { inputs -> CodeBlock.of('x') } as OperationCodegen

    @Shared PrivateTypeUniverse javac = new PrivateTypeUniverse()

    def method = Mock(ExecutableElement) {
        getSimpleName() >> Stub(Name) { toString() >> 'map' }
        getParameters() >> []
    }
    MethodScope scope = new MethodScope(method)
    MapperGraph graph = new MapperGraph()

    def 'a produced Value feeding an n-ary port or consumed twice hoists; a leaf or single-use one does not'() {
        // root <- assemble(p0<-shared, p1<-shared); shared <- prod(<-mid); mid <- prodMid(<-in)
        def shared = target('shared')
        def mid = target('mid')
        def leaf = source('in')
        operation(mid, [leaf])
        operation(shared, [mid])
        def root = target('')
        graph.markReturnRoot(root)
        operation(root, [shared, shared])
        def plan = ExtractedPlan.extract(graph)
        def hoist = HoistPlan.forMethod(graph, plan, root, [])

        expect: 'shared feeds an n-ary assemble twice -> hoisted; mid is single-use -> not; the leaf has no producer -> not'
        hoist.isHoisted(shared)
        !hoist.isHoisted(mid)
        !hoist.isHoisted(leaf)
    }

    def 'a produced Value consumed by two single-port operations hoists on the multi-use rule, not n-ary'() {
        // root <- assemble(p0<-x, p1<-y); x <- op(<-s); y <- op(<-s); s <- prod(<-in)
        def s = target('s')
        def x = target('x')
        def y = target('y')
        operation(s, [source('in')])
        operation(x, [s])
        operation(y, [s])
        def root = target('')
        graph.markReturnRoot(root)
        operation(root, [x, y])
        def hoist = HoistPlan.forMethod(graph, ExtractedPlan.extract(graph), root, [])

        expect: 's feeds two separate single-port operations (count > 1) though no n-ary port -> hoisted'
        hoist.isHoisted(s)
    }

    def 'declare allocates a slot-named local and reference returns its recorded expression'() {
        def value = target('name')
        def hoist = HoistPlan.forMethod(graph, ExtractedPlan.extract(graph), target(''), [])
        def name = hoist.declare(value)

        expect:
        name == 'name'
        hoist.reference(value).toString() == 'name'
    }

    def 'referencing a hoisted Value before it is declared fails fast'() {
        def hoist = HoistPlan.forMethod(graph, ExtractedPlan.extract(graph), target(''), [])

        when:
        hoist.reference(target('undeclared'))

        then:
        def error = thrown(IllegalStateException)

        expect:
        error.message.contains('referenced before declaration')
    }

    def 'a Value whose slot name is empty declares under the fallback name'() {
        // the return root at the empty target path has no slot name
        def hoist = HoistPlan.forMethod(graph, ExtractedPlan.extract(graph), target(''), [])

        expect:
        hoist.declare(target('')) == 'value'
    }

    def 'a lambda parameter is named after a declared element type'() {
        def hoist = HoistPlan.forMethod(graph, ExtractedPlan.extract(graph), target(''), [])

        expect:
        hoist.lambdaName(javac.STRING) == 'string'
    }

    def 'a lambda parameter over a non-declared element type falls back to element'() {
        def hoist = HoistPlan.forMethod(graph, ExtractedPlan.extract(graph), target(''), [])

        expect:
        hoist.lambdaName(javac.INT) == 'element'
    }

    private Value target(final String slot) {
        graph.valueFor(scope, new TargetLocation(TargetPath.of(slot)), javac.STRING, Nullability.NON_NULL)
    }

    private Value source(final String slot) {
        graph.valueFor(scope, new SourceLocation(AccessPath.of(slot)), javac.STRING, Nullability.NON_NULL)
    }

    private void operation(final Value out, final List<Value> portSources) {
        def ports = (0..<portSources.size()).collect { i ->
            new PortBinding(new Port('p' + i, portSources[i].type.get(), portSources[i].nullness.get()), av(portSources[i]))
        }
        graph.apply(new AddOperation('op', OP, 1, false, ports, av(out), Optional.empty()))
    }

    private AddValue av(final Value value) {
        new AddValue(value.scope, value.loc, value.type.get(), value.nullness.get())
    }
}
