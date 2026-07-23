package io.github.joke.percolate.processor.internal.stages.validate

import io.github.joke.percolate.lib.javapoet.CodeBlock
import io.github.joke.percolate.processor.Diagnostics
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
import io.github.joke.percolate.processor.model.MapperMappings
import io.github.joke.percolate.processor.model.MappingDirective
import io.github.joke.percolate.processor.model.MethodMappings
import io.github.joke.percolate.processor.test.FakeElements
import io.github.joke.percolate.processor.test.FakeType
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.Weights
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.annotation.processing.Messager
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import javax.tools.Diagnostic

/**
 * {@link ValidateOptionConsumptionStage} seam, unit-tested directly: computes {@code declared − consumed} for a
 * binding's {@code @Map} {@code format}/{@code zone} options against the operations of the graph's <strong>winning</strong>
 * plan (least-cost, {@link io.github.joke.percolate.processor.internal.graph.ExtractedPlan}), diagnosing any
 * declared key no operation in that plan stamped as consumed.
 */
@Tag('unit')
class ValidateOptionConsumptionStageSpec extends Specification {

    @Shared TypeMirror STRING = FakeType.declared('java.lang.String')

    def messager = Mock(Messager)
    def diagnostics = new Diagnostics(messager)
    @Subject
    def stage = new ValidateOptionConsumptionStage(diagnostics)

    def method = Mock(ExecutableElement) {
        getSimpleName() >> FakeElements.name('map')
        getParameters() >> []
    }
    def mirror = Mock(AnnotationMirror)
    def zoneValue = Mock(AnnotationValue)
    def formatValue = Mock(AnnotationValue)
    MethodScope scope = new MethodScope(method)

    def 'a zone declared on a winning plan that stamped no options is diagnosed as having no effect'() {
        given:
        def graph = new MapperGraph()
        landOp(graph, 'assign', Weights.STEP, [] as Set)
        def ctx = context(graph, directive('zone', zoneValue))

        when:
        stage.run(ctx)

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, { it.contains("'zone' has no effect") }, method, mirror,
                zoneValue)
    }

    def 'a zone consumed by the winning plan raises no diagnostic'() {
        given:
        def graph = new MapperGraph()
        landOp(graph, 'bridge', Weights.STEP, ['zone'] as Set)
        def ctx = context(graph, directive('zone', zoneValue))

        when:
        stage.run(ctx)

        then:
        0 * messager.printMessage(*_)
    }

    def 'a format consumed by the winning plan raises no diagnostic'() {
        given:
        def graph = new MapperGraph()
        landOp(graph, 'format', Weights.STEP, ['format'] as Set)
        def ctx = context(graph, directive('format', formatValue))

        when:
        stage.run(ctx)

        then:
        0 * messager.printMessage(*_)
    }

    def 'a directive with neither format nor zone is never diagnosed'() {
        given:
        def graph = new MapperGraph()
        landOp(graph, 'assign', Weights.STEP, [] as Set)
        def ctx = context(graph, new MappingDirective('', null, null, null, null, null, mirror, value(),
                null, null, null, null, null))

        when:
        stage.run(ctx)

        then:
        0 * messager.printMessage(*_)
    }

    def 'an option consumed only by a losing (non-winning) candidate is still diagnosed'() {
        given:
        def graph = new MapperGraph()
        def output = new AddValue(scope, root(), STRING, Nullability.NON_NULL)
        // cheap winner: consumes nothing
        def cheap = graph.apply(new AddOperation('cheap', { CodeBlock.of('x') } as OperationCodegen, Weights.STEP,
                false, [], output, Optional.empty(), [] as Set, []))
        // expensive loser: would have consumed zone, but costs more so never wins
        graph.apply(new AddOperation('expensive', { CodeBlock.of('x') } as OperationCodegen,
                Weights.STEP * 100, false, [], output, Optional.empty(), ['zone'] as Set, []))
        graph.markReturnRoot(graph.outputOf(cheap).get())
        def ctx = context(graph, directive('zone', zoneValue))

        when:
        stage.run(ctx)

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, { it.contains("'zone' has no effect") }, method, mirror,
                zoneValue)
    }

    def 'a zone consumed anywhere along a multi-hop winning plan raises no diagnostic'() {
        given:
        def graph = new MapperGraph()
        def bridgeOutput = new AddValue(scope, root(), STRING, Nullability.NON_NULL)
        def spokeInput = new AddValue(scope, source('in'), STRING, Nullability.NON_NULL)
        def bridge = graph.apply(new AddOperation('bridge', { CodeBlock.of('x') } as OperationCodegen, Weights.STEP,
                false, [new PortBinding(new Port('x', STRING, Nullability.NON_NULL), spokeInput)],
                bridgeOutput, Optional.empty(), ['zone'] as Set, []))
        graph.markReturnRoot(graph.outputOf(bridge).get())
        def ctx = context(graph, directive('zone', zoneValue))

        when:
        stage.run(ctx)

        then:
        0 * messager.printMessage(*_)
    }

    def 'a value shared by two ports in the winning plan is visited once, still contributing its consumed keys'() {
        given:
        def graph = new MapperGraph()
        def midOut = new AddValue(scope, new TargetLocation(new TargetPath(['mid'])), STRING, Nullability.NON_NULL)
        graph.apply(new AddOperation('mid', { CodeBlock.of('m') } as OperationCodegen, Weights.STEP,
                false, [], midOut, Optional.empty(), ['zone'] as Set, []))
        def rootOp = graph.apply(new AddOperation('assemble', { CodeBlock.of('r') } as OperationCodegen, Weights.STEP,
                false, [new PortBinding(new Port('a', STRING, Nullability.NON_NULL), midOut),
                        new PortBinding(new Port('b', STRING, Nullability.NON_NULL), midOut)],
                new AddValue(scope, root(), STRING, Nullability.NON_NULL), Optional.empty(), [] as Set, []))
        graph.markReturnRoot(graph.outputOf(rootOp).get())
        def ctx = context(graph, directive('zone', zoneValue))

        when:
        stage.run(ctx)

        then:
        0 * messager.printMessage(*_)
    }

    def 'a nested target path that resolves to a port is checked against that port\'s producer, not the root'() {
        given:
        def graph = new MapperGraph()
        def child = new AddValue(scope, new TargetLocation(new TargetPath(['x'])), STRING, Nullability.NON_NULL)
        graph.apply(new AddOperation('child', { CodeBlock.of('c') } as OperationCodegen, Weights.STEP,
                false, [], child, Optional.empty(), ['zone'] as Set, []))
        def rootOp = graph.apply(new AddOperation('build', { CodeBlock.of('build') } as OperationCodegen, Weights.STEP,
                false, [new PortBinding(new Port('x', STRING, Nullability.NON_NULL), child)],
                new AddValue(scope, root(), STRING, Nullability.NON_NULL), Optional.empty(), [] as Set, []))
        graph.markReturnRoot(graph.outputOf(rootOp).get())
        def ctx = context(graph, new MappingDirective('x', null, null, null, null, 'Europe/Berlin', mirror, value(),
                null, null, null, null, zoneValue))

        when:
        stage.run(ctx)

        then:
        0 * messager.printMessage(*_)
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
        def ctx = context(graph, new MappingDirective('x', null, null, null, null, 'Europe/Berlin', mirror, value(),
                null, null, null, null, zoneValue))

        when:
        stage.run(ctx)

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, { it.contains("'zone' has no effect") }, method, mirror,
                zoneValue)
    }

    def 'nothing is checked when the context has no mappings'() {
        given:
        def graph = new MapperGraph()
        landOp(graph, 'assign', Weights.STEP, [] as Set)
        def ctx = new MapperContext(Mock(TypeElement))
        ctx.graph = graph

        when:
        stage.run(ctx)

        then:
        0 * messager.printMessage(*_)
    }

    def 'nothing is checked when the context has no graph'() {
        given:
        def ctx = new MapperContext(Mock(TypeElement))
        ctx.mappings = new MapperMappings(null, [new MethodMappings(method, [directive('zone', zoneValue)])])

        when:
        stage.run(ctx)

        then:
        0 * messager.printMessage(*_)
    }

    private void landOp(final MapperGraph graph, final String label, final int weight, final Set<String> consumed) {
        def op = graph.apply(new AddOperation(label, { CodeBlock.of('x') } as OperationCodegen, weight, false, [],
                new AddValue(scope, root(), STRING, Nullability.NON_NULL), Optional.empty(), consumed, []))
        graph.markReturnRoot(graph.outputOf(op).get())
    }

    private MappingDirective directive(final String key, final AnnotationValue optionValue) {
        if (key == 'zone') {
            return new MappingDirective('', null, null, null, null, 'Europe/Berlin', mirror, value(),
                    null, null, null, null, optionValue)
        }
        new MappingDirective('', null, null, null, 'yyyy-MM-dd', null, mirror, value(),
                null, null, null, optionValue, null)
    }

    private TargetLocation root() {
        new TargetLocation(new TargetPath([]))
    }

    private SourceLocation source(final String segment) {
        new SourceLocation(new AccessPath([segment]))
    }

    private AnnotationValue value() {
        Mock(AnnotationValue)
    }

    private MapperContext context(final MapperGraph graph, final MappingDirective... directives) {
        def ctx = new MapperContext(Mock(TypeElement))
        ctx.graph = graph
        ctx.mappings = new MapperMappings(null, [new MethodMappings(method, directives as List)])
        ctx
    }
}
