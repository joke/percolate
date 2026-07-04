package io.github.joke.percolate.processor.internal.stages.validate

import com.palantir.javapoet.CodeBlock
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
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.test.PrivateTypeUniverse
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

import javax.annotation.processing.Messager
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import javax.tools.Diagnostic

/**
 * {@link ValidateConstantDefaultLegalityStage} seam, unit-tested directly: against the resolved target type (read off
 * a constructed {@link MapperGraph}) it diagnoses a constant/default that cannot be coerced, and a {@code defaultValue}
 * whose source can never be absent (a {@code NON_NULL} non-{@code Optional} reference or a primitive). Driven through
 * {@code run} with a hand-built graph + mappings; the target/source types come from {@link PrivateTypeUniverse}.
 */
@Tag('unit')
class ValidateConstantDefaultLegalityStageSpec extends Specification {

    @Shared PrivateTypeUniverse javac = new PrivateTypeUniverse()

    def messager = Mock(Messager)
    def diagnostics = new Diagnostics(messager)
    @Subject
    def stage = new ValidateConstantDefaultLegalityStage(diagnostics)

    def method = Mock(ExecutableElement) {
        getSimpleName() >> Stub(Name) { toString() >> 'map' }
        getParameters() >> []
    }
    def mirror = Mock(AnnotationMirror)
    def constantValue = Mock(AnnotationValue)
    def defaultValue = Mock(AnnotationValue)
    MethodScope scope = new MethodScope(method)

    def 'a constant that cannot be coerced to the target type is diagnosed at the constant value'() {
        given:
        def ctx = context(returnRoot(javac.INT, Nullability.NON_NULL),
                new MappingDirective('', null, 'abc', null, mirror, value(), null, constantValue, null))

        when:
        stage.run(ctx)

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, { it.contains("cannot coerce 'abc' to int") }, method, mirror,
                constantValue)
    }

    def 'a constant that coerces to the target type passes with no diagnostic'() {
        given:
        def ctx = context(returnRoot(javac.INT, Nullability.NON_NULL),
                new MappingDirective('', null, '42', null, mirror, value(), null, constantValue, null))

        when:
        stage.run(ctx)

        then:
        0 * messager.printMessage(*_)
    }

    def 'a defaultValue whose NON_NULL non-Optional source can never be absent is a dead default'() {
        given:
        def graph = new MapperGraph()
        graph.apply(new AddValue(scope, root(), javac.STRING, Nullability.NON_NULL))
        graph.apply(new AddValue(scope, source('in'), javac.STRING, Nullability.NON_NULL))
        def ctx = context(graph,
                new MappingDirective('', 'in', null, 'fallback', mirror, value(), value(), null, defaultValue))

        when:
        stage.run(ctx)

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, { it.contains('can never fire') }, method, mirror, defaultValue)
    }

    def 'a defaultValue with a NULLABLE source is live — no diagnostic'() {
        given:
        def graph = new MapperGraph()
        graph.apply(new AddValue(scope, root(), javac.STRING, Nullability.NON_NULL))
        graph.apply(new AddValue(scope, source('in'), javac.STRING, Nullability.NULLABLE))
        def ctx = context(graph,
                new MappingDirective('', 'in', null, 'fallback', mirror, value(), value(), null, defaultValue))

        when:
        stage.run(ctx)

        then:
        0 * messager.printMessage(*_)
    }

    def 'a directive with neither a constant nor a defaultValue is not checked'() {
        given:
        def ctx = context(returnRoot(javac.STRING, Nullability.NON_NULL),
                new MappingDirective('', 'in', null, null, mirror, value(), value(), null, null))

        when:
        stage.run(ctx)

        then:
        0 * messager.printMessage(*_)
    }

    def 'nothing is checked when the context has no mappings'() {
        def ctx = new MapperContext(Mock(TypeElement))
        ctx.graph = returnRoot(javac.INT, Nullability.NON_NULL)

        when:
        stage.run(ctx)

        then:
        0 * messager.printMessage(*_)
    }

    def 'nothing is checked when the context has no graph'() {
        def ctx = new MapperContext(Mock(TypeElement))
        ctx.mappings = new MapperMappings(null, [new MethodMappings(method,
                [new MappingDirective('', null, '42', null, mirror, value(), null, constantValue, null)])])

        when:
        stage.run(ctx)

        then:
        0 * messager.printMessage(*_)
    }

    def 'a constant whose target type cannot be resolved is not checked'() {
        // an empty graph — no typed return root, so the target type is unresolvable
        def ctx = context(new MapperGraph(),
                new MappingDirective('', null, '42', null, mirror, value(), null, constantValue, null))

        when:
        stage.run(ctx)

        then:
        0 * messager.printMessage(*_)
    }

    def 'a defaultValue that cannot be coerced to the target type is diagnosed at the default value'() {
        def ctx = context(returnRoot(javac.INT, Nullability.NON_NULL),
                new MappingDirective('', null, null, 'abc', mirror, value(), null, null, defaultValue))

        when:
        stage.run(ctx)

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, { it.contains("cannot coerce 'abc' to int") }, method, mirror,
                defaultValue)
    }

    def 'a defaultValue with no source and a coercible value passes with no diagnostic'() {
        def ctx = context(returnRoot(javac.STRING, Nullability.NON_NULL),
                new MappingDirective('', null, null, 'fallback', mirror, value(), null, null, defaultValue))

        when:
        stage.run(ctx)

        then:
        0 * messager.printMessage(*_)
    }

    def 'a defaultValue whose source is a primitive can never fire — a dead default'() {
        def graph = new MapperGraph()
        graph.apply(new AddValue(scope, root(), javac.STRING, Nullability.NON_NULL))
        graph.apply(new AddValue(scope, source('in'), javac.INT, Nullability.NON_NULL))
        def ctx = context(graph,
                new MappingDirective('', 'in', null, 'fallback', mirror, value(), value(), null, defaultValue))

        when:
        stage.run(ctx)

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, { it.contains('can never fire') }, method, mirror, defaultValue)
    }

    def 'a defaultValue whose NON_NULL Optional source is live — Optional can be empty, no diagnostic'() {
        def optionalOfString = javac.types().getDeclaredType(javac.element('java.util.Optional'),
                javac.STRING)
        def graph = new MapperGraph()
        graph.apply(new AddValue(scope, root(), javac.STRING, Nullability.NON_NULL))
        graph.apply(new AddValue(scope, source('in'), optionalOfString, Nullability.NON_NULL))
        def ctx = context(graph,
                new MappingDirective('', 'in', null, 'fallback', mirror, value(), value(), null, defaultValue))

        when:
        stage.run(ctx)

        then:
        0 * messager.printMessage(*_)
    }

    def 'a defaultValue whose NON_NULL array source can never be absent is a dead default'() {
        // an array is a non-Optional reference type, exercising the non-DeclaredType branch of isOptional
        def arrayOfString = javac.types().getArrayType(javac.STRING)
        def graph = new MapperGraph()
        graph.apply(new AddValue(scope, root(), javac.STRING, Nullability.NON_NULL))
        graph.apply(new AddValue(scope, source('in'), arrayOfString, Nullability.NON_NULL))
        def ctx = context(graph,
                new MappingDirective('', 'in', null, 'fallback', mirror, value(), value(), null, defaultValue))

        when:
        stage.run(ctx)

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, { it.contains('can never fire') }, method, mirror, defaultValue)
    }

    def 'a nested target type is resolved by walking the assembly port named by the path segment'() {
        // a root produced by an operation whose port "x" feeds an int child at tgt[x]
        def graph = nestedIntChildGraph()
        def ctx = context(graph,
                new MappingDirective('x', null, 'abc', null, mirror, value(), null, constantValue, null))

        when:
        stage.run(ctx)

        then: 'the walked child type (int) is what the constant is coerced against'
        1 * messager.printMessage(Diagnostic.Kind.ERROR, { it.contains("cannot coerce 'abc' to int") }, method, mirror,
                constantValue)
    }

    def 'a nested target whose path segment names no port is not checked'() {
        def ctx = context(nestedIntChildGraph(),
                new MappingDirective('y', null, '42', null, mirror, value(), null, constantValue, null))

        when:
        stage.run(ctx)

        then:
        0 * messager.printMessage(*_)
    }

    def 'a coercion failure to a declared target type names the simple type name'() {
        def ctx = context(returnRoot(javac.DAY_OF_WEEK, Nullability.NON_NULL),
                new MappingDirective('', null, 'NOTADAY', null, mirror, value(), null, constantValue, null))

        when:
        stage.run(ctx)

        then:
        1 * messager.printMessage(Diagnostic.Kind.ERROR, { it.contains("cannot coerce 'NOTADAY' to DayOfWeek") }, method,
                mirror, constantValue)
    }

    private MapperGraph nestedIntChildGraph() {
        def graph = new MapperGraph()
        def child = new AddValue(scope, new TargetLocation(new TargetPath(['x'])), javac.INT, Nullability.NON_NULL)
        graph.apply(new AddOperation('build', { CodeBlock.of('build') } as OperationCodegen, Weights.STEP, false,
                [new PortBinding(new Port('x', javac.INT, Nullability.NON_NULL), child)],
                new AddValue(scope, root(), javac.STRING, Nullability.NON_NULL), Optional.empty()))
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
