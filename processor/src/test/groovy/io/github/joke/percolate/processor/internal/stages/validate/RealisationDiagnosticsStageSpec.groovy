package io.github.joke.percolate.processor.internal.stages.validate

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.Diagnostics
import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.internal.graph.AccessPath
import io.github.joke.percolate.processor.internal.graph.AddOperation
import io.github.joke.percolate.processor.internal.graph.AddValue
import io.github.joke.percolate.processor.internal.graph.ChildScopeDecl
import io.github.joke.percolate.processor.internal.graph.MapperGraph
import io.github.joke.percolate.processor.internal.graph.MethodScope
import io.github.joke.percolate.processor.internal.graph.PortBinding
import io.github.joke.percolate.processor.internal.graph.SourceLocation
import io.github.joke.percolate.processor.internal.graph.TargetLocation
import io.github.joke.percolate.processor.internal.graph.TargetPath
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Isolated
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement

/**
 * {@link RealisationDiagnosticsStage} seam, unit-tested directly: each unreachable return-root {@code Value} (infinite
 * extraction cost over a constructed {@link MapperGraph}) is recorded on the context as a "no plan" message naming the
 * deepest unreachable demand and its type. Nothing is recorded when the graph is absent or the mapper already carries
 * errors (a targeted diagnostic already explains it).
 */
@Tag('unit')
@Isolated // bridge: shares the static TypeUniverse javac; serialise until the type-universe redesign (see openspec/notes.md)
class RealisationDiagnosticsStageSpec extends Specification {

    def method = Mock(ExecutableElement) {
        getSimpleName() >> Stub(Name) { toString() >> 'map' }
        getParameters() >> []
    }
    MethodScope scope = new MethodScope(method)
    def mapperType = Mock(TypeElement)
    def diagnostics = Mock(Diagnostics)
    @Subject
    def stage = new RealisationDiagnosticsStage(diagnostics)

    def 'an unreachable return root is recorded as a no-plan message naming the missing producer and type'() {
        given:
        diagnostics.hasErrorsFor(_) >> false
        def graph = new MapperGraph()
        def root = graph.apply(new AddValue(scope, root(), TypeUniverse.STRING, Nullability.NON_NULL))
        graph.markReturnRoot(root)
        def ctx = context(graph)

        when:
        stage.run(ctx)

        then:
        ctx.unsatisfiedRealisation.size() == 1
        ctx.unsatisfiedRealisation[0].contains('no plan for tgt[]')
        ctx.unsatisfiedRealisation[0].contains('java.lang.String')
    }

    def 'the deepest unreachable demand is descended to and named, not the return root itself'() {
        given:
        diagnostics.hasErrorsFor(_) >> false
        def graph = new MapperGraph()
        def child = new AddValue(scope, new TargetLocation(new TargetPath(['x'])), TypeUniverse.STRING, Nullability.NON_NULL)
        def operation = graph.apply(new AddOperation('build', { CodeBlock.of('x') } as OperationCodegen, Weights.STEP,
                false, [new PortBinding(new Port('x', TypeUniverse.STRING, Nullability.NON_NULL), child)],
                new AddValue(scope, root(), TypeUniverse.STRING, Nullability.NON_NULL), Optional.empty()))
        graph.markReturnRoot(graph.outputOf(operation).get())
        def ctx = context(graph)

        when:
        stage.run(ctx)

        then: 'the message blames the unreachable child demand tgt[x], not the root'
        ctx.unsatisfiedRealisation.size() == 1
        ctx.unsatisfiedRealisation[0].contains('tgt[x] has no producer')
    }

    def 'nothing is recorded when the mapper already carries errors'() {
        given:
        diagnostics.hasErrorsFor(mapperType) >> true
        def graph = new MapperGraph()
        graph.markReturnRoot(graph.apply(new AddValue(scope, root(), TypeUniverse.STRING, Nullability.NON_NULL)))

        when:
        def ctx = context(graph)
        stage.run(ctx)

        then:
        ctx.unsatisfiedRealisation.empty
    }

    def 'nothing is recorded when there is no graph'() {
        given:
        def ctx = new MapperContext(mapperType)

        when:
        stage.run(ctx)

        then:
        ctx.unsatisfiedRealisation.empty
    }

    def 'a reachable return root records no message'() {
        // a root produced by a nullary (portless) operation — finite cost, so reachable
        diagnostics.hasErrorsFor(_) >> false
        def graph = new MapperGraph()
        def operation = graph.apply(new AddOperation('supply', { CodeBlock.of('x') } as OperationCodegen, Weights.STEP,
                false, [], new AddValue(scope, root(), TypeUniverse.STRING, Nullability.NON_NULL), Optional.empty()))
        graph.markReturnRoot(graph.outputOf(operation).get())
        def ctx = context(graph)

        when:
        stage.run(ctx)

        then:
        ctx.unsatisfiedRealisation.empty
    }

    def 'the deepest miss on a source-path demand is labelled by its node id, not a target path'() {
        // a producer whose reachable leaf port is skipped for the unreachable multi-segment ACCESS port
        diagnostics.hasErrorsFor(_) >> false
        def graph = new MapperGraph()
        def leaf = new AddValue(scope, new SourceLocation(new AccessPath(['in'])), TypeUniverse.STRING,
                Nullability.NON_NULL)
        def access = new AddValue(scope, new SourceLocation(new AccessPath(['in', 'missing'])), TypeUniverse.STRING,
                Nullability.NON_NULL)
        def operation = graph.apply(new AddOperation('build', { CodeBlock.of('x') } as OperationCodegen, Weights.STEP,
                false,
                [new PortBinding(new Port('a', TypeUniverse.STRING, Nullability.NON_NULL), leaf),
                 new PortBinding(new Port('b', TypeUniverse.STRING, Nullability.NON_NULL), access)],
                new AddValue(scope, root(), TypeUniverse.STRING, Nullability.NON_NULL), Optional.empty()))
        graph.markReturnRoot(graph.outputOf(operation).get())
        def ctx = context(graph)

        when:
        stage.run(ctx)

        then: 'the message blames the ACCESS demand by node id, not a tgt[...] label'
        ctx.unsatisfiedRealisation[0].contains('src[in.missing]')
    }

    def 'an operation unreachable only through its child scope blames the root demand itself'() {
        // a scope-owning operation with no unsatisfied port — its child return-root is what is unreachable
        diagnostics.hasErrorsFor(_) >> false
        def graph = new MapperGraph()
        def childScope = new ChildScopeDecl(TypeUniverse.STRING, Nullability.NON_NULL, TypeUniverse.STRING,
                Nullability.NON_NULL)
        def operation = graph.apply(new AddOperation('map', { CodeBlock.of('x') } as OperationCodegen, Weights.STEP,
                false, [], new AddValue(scope, root(), TypeUniverse.LIST_OF_STRING, Nullability.NON_NULL),
                Optional.of(childScope)))
        graph.markReturnRoot(graph.outputOf(operation).get())
        def ctx = context(graph)

        when:
        stage.run(ctx)

        then:
        ctx.unsatisfiedRealisation[0].contains('no plan for tgt[]')
    }

    def 'a producer cycle among unreachable demands terminates and reports the revisited demand'() {
        // root <- opA(port p -> child); child <- opB(port q -> root): a 2-cycle, both unreachable
        diagnostics.hasErrorsFor(_) >> false
        def graph = new MapperGraph()
        def rootAdd = new AddValue(scope, root(), TypeUniverse.STRING, Nullability.NON_NULL)
        def childAdd = new AddValue(scope, new TargetLocation(new TargetPath(['b'])), TypeUniverse.STRING,
                Nullability.NON_NULL)
        def opA = graph.apply(new AddOperation('a', { CodeBlock.of('x') } as OperationCodegen, Weights.STEP, false,
                [new PortBinding(new Port('p', TypeUniverse.STRING, Nullability.NON_NULL), childAdd)], rootAdd,
                Optional.empty()))
        graph.apply(new AddOperation('b', { CodeBlock.of('x') } as OperationCodegen, Weights.STEP, false,
                [new PortBinding(new Port('q', TypeUniverse.STRING, Nullability.NON_NULL), rootAdd)], childAdd,
                Optional.empty()))
        graph.markReturnRoot(graph.outputOf(opA).get())
        def ctx = context(graph)

        when:
        stage.run(ctx)

        then:
        ctx.unsatisfiedRealisation.size() == 1
    }

    private static TargetLocation root() {
        new TargetLocation(new TargetPath([]))
    }

    private MapperContext context(final MapperGraph graph) {
        def ctx = new MapperContext(mapperType)
        ctx.graph = graph
        ctx
    }
}
