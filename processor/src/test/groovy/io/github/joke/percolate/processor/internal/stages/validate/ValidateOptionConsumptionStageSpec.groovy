package io.github.joke.percolate.processor.internal.stages.validate

import io.github.joke.percolate.lib.javapoet.CodeBlock
import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.internal.graph.AccessPath
import io.github.joke.percolate.processor.internal.graph.AddOperation
import io.github.joke.percolate.processor.internal.graph.AddValue
import io.github.joke.percolate.processor.internal.graph.MapperGraph
import io.github.joke.percolate.processor.internal.graph.MethodScope
import io.github.joke.percolate.processor.internal.graph.PortBinding
import io.github.joke.percolate.processor.internal.graph.SourceLocation
import io.github.joke.percolate.processor.internal.graph.TargetLocation
import io.github.joke.percolate.processor.internal.graph.TargetPath
import io.github.joke.percolate.processor.model.Bind
import io.github.joke.percolate.processor.model.MethodDirectives
import io.github.joke.percolate.processor.test.FakeElements
import io.github.joke.percolate.processor.test.FakeType
import io.github.joke.percolate.spi.DirectiveInput
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.Subjects
import io.github.joke.percolate.spi.Weights
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror

/**
 * {@link ValidateOptionConsumptionStage} seam, unit-tested directly: computes {@code declared − consumed} for every
 * target path a reader attached inputs to, against the operations of the graph's <strong>winning</strong> plan
 * (least-cost, {@link io.github.joke.percolate.processor.internal.graph.ExtractedPlan}), reporting a permanent
 * diagnostic (design D14) for any declared {@link DirectiveInput} no operation in that plan stamped as consumed.
 */
@Tag('unit')
class ValidateOptionConsumptionStageSpec extends Specification {

    @Shared TypeMirror STRING = FakeType.declared('java.lang.String')

    @Subject
    def stage = new ValidateOptionConsumptionStage()

    def method = Mock(ExecutableElement) {
        getSimpleName() >> FakeElements.name('map')
        getParameters() >> []
    }
    MethodScope scope = new MethodScope(method)

    def 'a zone declared on a winning plan that stamped no options is diagnosed as having no effect'() {
        given:
        def graph = new MapperGraph()
        landOp(graph, 'assign', Weights.STEP, [] as Set)
        def ctx = context(graph, '', zoneInput())

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.size() == 1
        with(ctx.diagnostics[0]) {
            permanent
            message.contains("'zone' has no effect")
        }
    }

    def 'a zone consumed by the winning plan raises no diagnostic'() {
        given:
        def graph = new MapperGraph()
        def zone = zoneInput()
        landOp(graph, 'bridge', Weights.STEP, [zone] as Set)
        def ctx = context(graph, '', zone)

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.empty
    }

    def 'a format consumed by the winning plan raises no diagnostic'() {
        given:
        def graph = new MapperGraph()
        def format = formatInput()
        landOp(graph, 'format', Weights.STEP, [format] as Set)
        def ctx = context(graph, '', format)

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.empty
    }

    def 'a path with no attached inputs is never diagnosed'() {
        given:
        def graph = new MapperGraph()
        landOp(graph, 'assign', Weights.STEP, [] as Set)
        def ctx = new MapperContext(Mock(TypeElement))
        ctx.graph = graph
        ctx.methodDirectives = [new MethodDirectives(method, [], [:], [], [:])]

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.empty
    }

    def 'an input attached at a path with no bind (e.g. a method-level @MapEnum table) is never checked'() {
        given: 'nothing lands at all, so a bound path\'s zone would ordinarily be flagged'
        def graph = new MapperGraph()
        def ctx = new MapperContext(Mock(TypeElement))
        ctx.graph = graph
        ctx.methodDirectives = [new MethodDirectives(method, [], ['': [zoneInput()]], [], [:])]

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.empty
    }

    def 'an option consumed only by a losing (non-winning) candidate is still diagnosed'() {
        given:
        def graph = new MapperGraph()
        def output = new AddValue(scope, root(), STRING, Nullability.NON_NULL)
        def zone = zoneInput()
        // cheap winner: consumes nothing
        def cheap = graph.apply(new AddOperation('cheap', { CodeBlock.of('x') } as OperationCodegen, Weights.STEP,
                false, [], output, Optional.empty(), [] as Set, []))
        // expensive loser: would have consumed zone, but costs more so never wins
        graph.apply(new AddOperation('expensive', { CodeBlock.of('x') } as OperationCodegen,
                Weights.STEP * 100, false, [], output, Optional.empty(), [zone] as Set, []))
        graph.markReturnRoot(graph.outputOf(cheap).get())
        def ctx = context(graph, '', zone)

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.size() == 1
        ctx.diagnostics[0].message.contains("'zone' has no effect")
    }

    def 'a zone consumed anywhere along a multi-hop winning plan raises no diagnostic'() {
        given:
        def graph = new MapperGraph()
        def zone = zoneInput()
        def bridgeOutput = new AddValue(scope, root(), STRING, Nullability.NON_NULL)
        def spokeInput = new AddValue(scope, source('in'), STRING, Nullability.NON_NULL)
        def bridge = graph.apply(new AddOperation('bridge', { CodeBlock.of('x') } as OperationCodegen, Weights.STEP,
                false, [new PortBinding(new Port('x', STRING, Nullability.NON_NULL), spokeInput)],
                bridgeOutput, Optional.empty(), [zone] as Set, []))
        graph.markReturnRoot(graph.outputOf(bridge).get())
        def ctx = context(graph, '', zone)

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.empty
    }

    def 'a value shared by two ports in the winning plan is visited once, still contributing its consumed keys'() {
        given:
        def graph = new MapperGraph()
        def zone = zoneInput()
        def midOut = new AddValue(scope, new TargetLocation(new TargetPath(['mid'])), STRING, Nullability.NON_NULL)
        graph.apply(new AddOperation('mid', { CodeBlock.of('m') } as OperationCodegen, Weights.STEP,
                false, [], midOut, Optional.empty(), [zone] as Set, []))
        def rootOp = graph.apply(new AddOperation('assemble', { CodeBlock.of('r') } as OperationCodegen, Weights.STEP,
                false, [new PortBinding(new Port('a', STRING, Nullability.NON_NULL), midOut),
                        new PortBinding(new Port('b', STRING, Nullability.NON_NULL), midOut)],
                new AddValue(scope, root(), STRING, Nullability.NON_NULL), Optional.empty(), [] as Set, []))
        graph.markReturnRoot(graph.outputOf(rootOp).get())
        def ctx = context(graph, '', zone)

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.empty
    }

    def 'a nested target path that resolves to a port is checked against that port\'s producer, not the root'() {
        given:
        def graph = new MapperGraph()
        def zone = zoneInput()
        def child = new AddValue(scope, new TargetLocation(new TargetPath(['x'])), STRING, Nullability.NON_NULL)
        graph.apply(new AddOperation('child', { CodeBlock.of('c') } as OperationCodegen, Weights.STEP,
                false, [], child, Optional.empty(), [zone] as Set, []))
        def rootOp = graph.apply(new AddOperation('build', { CodeBlock.of('build') } as OperationCodegen, Weights.STEP,
                false, [new PortBinding(new Port('x', STRING, Nullability.NON_NULL), child)],
                new AddValue(scope, root(), STRING, Nullability.NON_NULL), Optional.empty(), [] as Set, []))
        graph.markReturnRoot(graph.outputOf(rootOp).get())
        def ctx = context(graph, 'x', zone)

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.empty
    }

    def 'a declared option whose nested target path names no port is diagnosed (nothing could have consumed it)'() {
        given:
        def graph = new MapperGraph()
        // root's assembly op declares only port "y" — a directive naming target "x" cannot be resolved
        def child = new AddValue(scope, new TargetLocation(new TargetPath(['y'])), STRING, Nullability.NON_NULL)
        def op = graph.apply(new AddOperation('build', { CodeBlock.of('build') } as OperationCodegen, Weights.STEP,
                false, [new PortBinding(new Port('y', STRING, Nullability.NON_NULL), child)],
                new AddValue(scope, root(), STRING, Nullability.NON_NULL), Optional.empty(), [] as Set, []))
        graph.markReturnRoot(graph.outputOf(op).get())
        def ctx = context(graph, 'x', zoneInput())

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.size() == 1
        ctx.diagnostics[0].message.contains("'zone' has no effect")
    }

    def 'nothing is checked when the context has no method directives'() {
        given:
        def graph = new MapperGraph()
        landOp(graph, 'assign', Weights.STEP, [] as Set)
        def ctx = new MapperContext(Mock(TypeElement))
        ctx.graph = graph

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.empty
    }

    def 'nothing is checked when the context has no graph'() {
        given:
        def ctx = new MapperContext(Mock(TypeElement))
        ctx.methodDirectives = [new MethodDirectives(method, [], ['': [zoneInput()]], [], [:])]

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.empty
    }

    private void landOp(final MapperGraph graph, final String label, final int weight, final Set<DirectiveInput> consumed) {
        def op = graph.apply(new AddOperation(label, { CodeBlock.of('x') } as OperationCodegen, weight, false, [],
                new AddValue(scope, root(), STRING, Nullability.NON_NULL), Optional.empty(), consumed, []))
        graph.markReturnRoot(graph.outputOf(op).get())
    }

    private DirectiveInput zoneInput(final String zone = 'Europe/Berlin') {
        DirectiveInput.scalar('zone', zone, Subjects.none())
    }

    private DirectiveInput formatInput(final String pattern = 'yyyy-MM-dd') {
        DirectiveInput.scalar('format', pattern, Subjects.none())
    }

    private TargetLocation root() {
        new TargetLocation(new TargetPath([]))
    }

    private SourceLocation source(final String segment) {
        new SourceLocation(new AccessPath([segment]))
    }

    private MapperContext context(final MapperGraph graph, final String targetPath, final DirectiveInput input) {
        def ctx = new MapperContext(Mock(TypeElement))
        ctx.graph = graph
        def bind = new Bind(targetPath.empty ? [] : targetPath.split('\\.').toList(), [], Subjects.none())
        ctx.methodDirectives = [new MethodDirectives(method, [bind], [(targetPath): [input]], [], [:])]
        ctx
    }
}
