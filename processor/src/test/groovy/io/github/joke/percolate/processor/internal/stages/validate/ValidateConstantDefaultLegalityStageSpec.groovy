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
import io.github.joke.percolate.processor.model.MapperMappings
import io.github.joke.percolate.processor.model.MappingDirective
import io.github.joke.percolate.processor.model.MethodMappings
import io.github.joke.percolate.processor.test.FakeElements
import io.github.joke.percolate.processor.test.FakeType
import io.github.joke.percolate.processor.test.MappingDirectives
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.Weights
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

/**
 * {@link ValidateConstantDefaultLegalityStage} seam, unit-tested directly: against the resolved target type (read off
 * a constructed {@link MapperGraph}) it reports a permanent diagnostic (design D14) for a constant/default that
 * cannot be coerced, and a {@code defaultValue} whose source can never be absent (a {@code NON_NULL} non-{@code
 * Optional} reference or a primitive). Driven through {@code run} with a hand-built graph + mappings.
 *
 * <p>Unit-tested mock-only (change {@code type-query-seam}): the stage never reaches a {@code ResolveCtx} (it is
 * constructed per-mapper only inside {@code ExpandStage}), so its {@code TypeMirror}/{@code Element} navigation is
 * single-hop and a {@link FakeType} token stands in for the compiled type without javac.
 */
@Tag('unit')
class ValidateConstantDefaultLegalityStageSpec extends Specification {

    @Shared TypeMirror INT = FakeType.marker(TypeKind.INT)
    @Shared TypeMirror STRING = FakeType.declared('java.lang.String')
    @Shared TypeMirror DAY_OF_WEEK = FakeType.declared('DayOfWeek')

    @Subject
    def stage = new ValidateConstantDefaultLegalityStage()

    def method = Mock(ExecutableElement) {
        getSimpleName() >> FakeElements.name('map')
        getParameters() >> []
    }
    MethodScope scope = new MethodScope(method)

    def 'a constant that cannot be coerced to the target type is diagnosed at the constant value'() {
        given:
        def ctx = context(returnRoot(INT, Nullability.NON_NULL),
                MappingDirectives.of('', [constant: 'abc']))

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.size() == 1
        with(ctx.diagnostics[0]) {
            permanent
            message.contains("cannot coerce 'abc' to int")
        }
    }

    def 'a constant that coerces to the target type passes with no diagnostic'() {
        given:
        def ctx = context(returnRoot(INT, Nullability.NON_NULL),
                MappingDirectives.of('', [constant: '42']))

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.empty
    }

    def 'a defaultValue whose NON_NULL non-Optional source can never be absent is a dead default'() {
        given:
        def graph = new MapperGraph()
        graph.apply(new AddValue(scope, root(), STRING, Nullability.NON_NULL))
        graph.apply(new AddValue(scope, source('in'), STRING, Nullability.NON_NULL))
        def ctx = context(graph,
                MappingDirectives.of('', [source: 'in', defaultValue: 'fallback']))

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.size() == 1
        with(ctx.diagnostics[0]) {
            permanent
            message.contains('can never fire')
        }
    }

    def 'a defaultValue with a NULLABLE source is live — no diagnostic'() {
        given:
        def graph = new MapperGraph()
        graph.apply(new AddValue(scope, root(), STRING, Nullability.NON_NULL))
        graph.apply(new AddValue(scope, source('in'), STRING, Nullability.NULLABLE))
        def ctx = context(graph,
                MappingDirectives.of('', [source: 'in', defaultValue: 'fallback']))

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.empty
    }

    def 'a directive with neither a constant nor a defaultValue is not checked'() {
        given:
        def ctx = context(returnRoot(STRING, Nullability.NON_NULL),
                MappingDirectives.of('', [source: 'in']))

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.empty
    }

    def 'nothing is checked when the context has no mappings'() {
        def ctx = new MapperContext(Mock(TypeElement))
        ctx.graph = returnRoot(INT, Nullability.NON_NULL)

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.empty
    }

    def 'nothing is checked when the context has no graph'() {
        def ctx = new MapperContext(Mock(TypeElement))
        ctx.mappings = new MapperMappings(null, [new MethodMappings(method,
                [MappingDirectives.of('', [constant: '42'])])])

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.empty
    }

    def 'a constant whose target type cannot be resolved is not checked'() {
        // an empty graph — no typed return root, so the target type is unresolvable
        def ctx = context(new MapperGraph(),
                MappingDirectives.of('', [constant: '42']))

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.empty
    }

    def 'a defaultValue that cannot be coerced to the target type is diagnosed at the default value'() {
        def ctx = context(returnRoot(INT, Nullability.NON_NULL),
                MappingDirectives.of('', [defaultValue: 'abc']))

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.size() == 1
        ctx.diagnostics[0].message.contains("cannot coerce 'abc' to int")
    }

    def 'a defaultValue with no source and a coercible value passes with no diagnostic'() {
        def ctx = context(returnRoot(STRING, Nullability.NON_NULL),
                MappingDirectives.of('', [defaultValue: 'fallback']))

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.empty
    }

    def 'a defaultValue whose source is a primitive can never fire — a dead default'() {
        def graph = new MapperGraph()
        graph.apply(new AddValue(scope, root(), STRING, Nullability.NON_NULL))
        graph.apply(new AddValue(scope, source('in'), INT, Nullability.NON_NULL))
        def ctx = context(graph,
                MappingDirectives.of('', [source: 'in', defaultValue: 'fallback']))

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.size() == 1
        ctx.diagnostics[0].message.contains('can never fire')
    }

    def 'a defaultValue whose NON_NULL Optional source is live — Optional can be empty, no diagnostic'() {
        def optionalOfString = FakeType.declared('java.util.Optional', STRING)
        def graph = new MapperGraph()
        graph.apply(new AddValue(scope, root(), STRING, Nullability.NON_NULL))
        graph.apply(new AddValue(scope, source('in'), optionalOfString, Nullability.NON_NULL))
        def ctx = context(graph,
                MappingDirectives.of('', [source: 'in', defaultValue: 'fallback']))

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.empty
    }

    def 'a defaultValue whose NON_NULL array source can never be absent is a dead default'() {
        // an array is a non-Optional reference type, exercising the non-DeclaredType branch of isOptional
        def arrayOfString = FakeType.array(STRING)
        def graph = new MapperGraph()
        graph.apply(new AddValue(scope, root(), STRING, Nullability.NON_NULL))
        graph.apply(new AddValue(scope, source('in'), arrayOfString, Nullability.NON_NULL))
        def ctx = context(graph,
                MappingDirectives.of('', [source: 'in', defaultValue: 'fallback']))

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.size() == 1
        ctx.diagnostics[0].message.contains('can never fire')
    }

    def 'a nested target type is resolved by walking the assembly port named by the path segment'() {
        // a root produced by an operation whose port "x" feeds an int child at tgt[x]
        def graph = nestedIntChildGraph()
        def ctx = context(graph,
                MappingDirectives.of('x', [constant: 'abc']))

        when:
        stage.run(ctx)

        then: 'the walked child type (int) is what the constant is coerced against'
        ctx.diagnostics.size() == 1
        ctx.diagnostics[0].message.contains("cannot coerce 'abc' to int")
    }

    def 'a nested target whose path segment names no port is not checked'() {
        def ctx = context(nestedIntChildGraph(),
                MappingDirectives.of('y', [constant: '42']))

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.empty
    }

    def 'a coercion failure to a declared target type names the simple type name'() {
        def ctx = context(returnRoot(DAY_OF_WEEK, Nullability.NON_NULL),
                MappingDirectives.of('', [constant: 'NOTADAY']))

        when:
        stage.run(ctx)

        then:
        ctx.diagnostics.size() == 1
        ctx.diagnostics[0].message.contains("cannot coerce 'NOTADAY' to DayOfWeek")
    }

    private MapperGraph nestedIntChildGraph() {
        def graph = new MapperGraph()
        def child = new AddValue(scope, new TargetLocation(new TargetPath(['x'])), INT, Nullability.NON_NULL)
        graph.apply(new AddOperation('build', { CodeBlock.of('build') } as OperationCodegen, Weights.STEP, false,
                [new PortBinding(new Port('x', INT, Nullability.NON_NULL), child)],
                new AddValue(scope, root(), STRING, Nullability.NON_NULL), Optional.empty(), [] as Set, []))
        graph
    }

    private MapperGraph returnRoot(final TypeMirror type, final Nullability nullness) {
        def graph = new MapperGraph()
        graph.apply(new AddValue(scope, root(), type, nullness))
        graph
    }

    private TargetLocation root() {
        new TargetLocation(new TargetPath([]))
    }

    private SourceLocation source(final String segment) {
        new SourceLocation(new AccessPath([segment]))
    }

    private MapperContext context(final MapperGraph graph, final MappingDirective... directives) {
        def ctx = new MapperContext(Mock(TypeElement))
        ctx.graph = graph
        ctx.mappings = new MapperMappings(null, [new MethodMappings(method, directives as List)])
        ctx
    }
}
