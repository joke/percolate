package io.github.joke.percolate.processor.internal.stages.generate

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.ProcessorOptions
import io.github.joke.percolate.processor.internal.graph.AccessPath
import io.github.joke.percolate.processor.internal.graph.AddOperation
import io.github.joke.percolate.processor.internal.graph.AddValue
import io.github.joke.percolate.processor.internal.graph.ChildScope
import io.github.joke.percolate.processor.internal.graph.ChildScopeDecl
import io.github.joke.percolate.processor.internal.graph.ElementLocation
import io.github.joke.percolate.processor.internal.graph.MapperGraph
import io.github.joke.percolate.processor.internal.graph.MethodScope
import io.github.joke.percolate.processor.internal.graph.Operation
import io.github.joke.percolate.processor.internal.graph.PortBinding
import io.github.joke.percolate.processor.internal.graph.SourceLocation
import io.github.joke.percolate.processor.internal.graph.TargetLocation
import io.github.joke.percolate.processor.internal.graph.TargetPath
import io.github.joke.percolate.processor.internal.graph.Value
import io.github.joke.percolate.processor.model.MapperShape
import io.github.joke.percolate.spi.Codegen
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.OperationCodegen
import io.github.joke.percolate.spi.Port
import io.github.joke.percolate.spi.ScopeCodegen
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Isolated
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror

/**
 * {@link BuildMethodBodies} seam, unit-tested directly: each abstract method body is composed by walking the
 * {@link io.github.joke.percolate.processor.internal.graph.ExtractedPlan} from the return root. A single-port chain
 * renders inline; a Value shared by two consumers is hoisted to a local and referenced; the {@code docTags} option
 * brackets the body. Driven over a hand-built reachable graph with real codegen (the {@code ExtractedPlanSpec}
 * helper pattern, on a {@link MethodScope}).
 */
@Tag('unit')
@Isolated // bridge: shares the static TypeUniverse javac; serialise until the type-universe redesign (see openspec/notes.md)
class BuildMethodBodiesSpec extends Specification {

    static final OperationCodegen IDENTITY = { inputs -> inputs.single() } as OperationCodegen
    static final ScopeCodegen MAP =
            { operand, var, body -> CodeBlock.of('$L.map($N -> $L)', operand, var, body) } as ScopeCodegen

    def method = Mock(ExecutableElement) {
        getSimpleName() >> Stub(Name) { toString() >> 'map' }
        getParameters() >> [param('in')]
    }
    MethodScope scope = new MethodScope(method)
    MapperGraph graph = new MapperGraph()
    Value root = graph.valueFor(scope, new TargetLocation(TargetPath.of('')), TypeUniverse.STRING, Nullability.NON_NULL)
            .tap { graph.markReturnRoot(it) }

    def 'a single-producer return renders inline as the chosen producer expression'() {
        given:
        operation(root, [source('in')], IDENTITY)

        when:
        def bodies = engine(false, false, false).build(context())

        then:
        bodies.size() == 1
        bodies[0].method == method
        bodies[0].body.toString() == 'return in;\n'
    }

    def 'the docTags option brackets the method body in AsciiDoc include tags named after the method'() {
        given:
        operation(root, [source('in')], IDENTITY)

        expect:
        engine(false, false, true).build(context())[0].body.toString() ==
                '// tag::map[]\nreturn in;\n// end::map[]\n'
    }

    def 'a Value shared by two consumer ports is hoisted to a local and referenced by name'() {
        given: 'root := shared + shared, where shared := in (shared feeds two ports, so it hoists)'
        def shared = intermediate('m')
        operation(shared, [source('in')], IDENTITY)
        operation(root, [shared, shared],
                { inputs -> CodeBlock.of('$L + $L', inputs.byGroupPosition(0), inputs.byGroupPosition(1)) } as OperationCodegen)

        when:
        def body = engine(false, false, false).build(context())[0].body.toString()

        then: 'the shared Value is declared once as a local and referenced, not inlined twice'
        body.contains('String m = in;')
        body.contains('return m + m;')
    }

    def 'locals.final and locals.var control the hoisted declaration syntax'() {
        given:
        def shared = intermediate('m')
        operation(shared, [source('in')], IDENTITY)
        operation(root, [shared, shared],
                { inputs -> CodeBlock.of('$L + $L', inputs.byGroupPosition(0), inputs.byGroupPosition(1)) } as OperationCodegen)

        expect:
        engine(makeFinal, useVar, false).build(context())[0].body.toString().contains(declaration)

        where: 'javapoet renders the explicit type as its FQN; var omits it'
        makeFinal | useVar | declaration
        false     | false  | 'java.lang.String m = in;'
        true      | false  | 'final java.lang.String m = in;'
        false     | true   | 'var m = in;'
    }

    def 'a produced intermediate feeding a single port stays inline, not hoisted'() {
        // root := f(mid), mid := g(in): mid has a producer but one consumer, so it is threaded inline
        def mid = intermediate('m')
        operation(mid, [source('in')], IDENTITY)
        operation(root, [mid], IDENTITY)

        when:
        def body = engine(false, false, false).build(context())[0].body.toString()

        then:
        body == 'return in;\n'
    }

    def 'build returns no bodies when the shape is absent'() {
        def ctx = new MapperContext(Mock(TypeElement))
        ctx.graph = graph

        expect:
        engine(false, false, false).build(ctx).empty
    }

    def 'build returns no bodies when the graph is absent'() {
        def ctx = new MapperContext(Mock(TypeElement))
        ctx.shape = new MapperShape(Mock(TypeElement), [method])

        expect:
        engine(false, false, false).build(ctx).empty
    }

    def 'a container mapping weaves the container codegen around an expression lambda when the child hoists nothing'() {
        // List<String> in -> List<Integer> out; child String -> Integer sources straight from the element
        def g = new MapperGraph()
        def containerOp = containerMapping(g, TypeUniverse.LIST_OF_INT)
        def child = containerOp.childScope.get()
        def element = new AddValue(child, new ElementLocation(), TypeUniverse.STRING, Nullability.NON_NULL)
        g.apply(new AddOperation('conv', IDENTITY, 1, false,
                [new PortBinding(new Port('e', TypeUniverse.STRING, Nullability.NON_NULL), element)],
                childRoot(child), Optional.empty()))

        when:
        def body = engine(false, false, false).build(containerContext(g)).first().body.toString()

        then:
        body == 'return in.map(string -> string);\n'
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    /** A scope-owning List<String> -> List<Integer> map operation; its child return root is Integer. */
    private Operation containerMapping(final MapperGraph g, final TypeMirror output) {
        def sourceIn = new AddValue(scope, new SourceLocation(AccessPath.of('in')), TypeUniverse.LIST_OF_STRING,
                Nullability.NON_NULL)
        def decl = new ChildScopeDecl(TypeUniverse.STRING, Nullability.NON_NULL, TypeUniverse.INTEGER,
                Nullability.NON_NULL)
        def op = g.apply(new AddOperation('map', MAP, Weights.CONTAINER, false,
                [new PortBinding(new Port('src', TypeUniverse.LIST_OF_STRING, Nullability.NON_NULL), sourceIn)],
                new AddValue(scope, new TargetLocation(TargetPath.of('')), output, Nullability.NON_NULL),
                Optional.of(decl)))
        g.markReturnRoot(g.outputOf(op).get())
        op
    }

    private AddValue childRoot(final ChildScope child) {
        new AddValue(child, new TargetLocation(TargetPath.of('')), TypeUniverse.INTEGER, Nullability.NON_NULL)
    }

    private MapperContext containerContext(final MapperGraph g) {
        def ctx = new MapperContext(Mock(TypeElement))
        ctx.shape = new MapperShape(Mock(TypeElement), [method])
        ctx.graph = g
        ctx
    }

    private BuildMethodBodies engine(final boolean makeFinal, final boolean useVar, final boolean docTags) {
        new BuildMethodBodies(new ProcessorOptions(false, [] as Set, makeFinal, useVar, docTags))
    }

    private Value source(final String slot) {
        graph.valueFor(scope, new SourceLocation(AccessPath.of(slot)), TypeUniverse.STRING, Nullability.NON_NULL)
    }

    private Value intermediate(final String slot) {
        graph.valueFor(scope, new TargetLocation(TargetPath.of(slot)), TypeUniverse.STRING, Nullability.NON_NULL)
    }

    private Operation operation(final Value out, final List<Value> portSources, final Codegen codegen) {
        def ports = (0..<portSources.size()).collect { i ->
            new PortBinding(new Port('p' + i, portSources[i].type.get(), portSources[i].nullness.get()), av(portSources[i]))
        }
        graph.apply(new AddOperation('op', codegen, 1, false, ports, av(out), Optional.empty()))
    }

    private AddValue av(final Value value) {
        new AddValue(value.scope, value.loc, value.type.get(), value.nullness.get())
    }

    private MapperContext context() {
        def ctx = new MapperContext(Mock(TypeElement))
        ctx.shape = new MapperShape(Mock(TypeElement), [method])
        ctx.graph = graph
        ctx
    }

    private VariableElement param(final String paramName) {
        Mock(VariableElement) {
            getSimpleName() >> Stub(Name) { toString() >> paramName }
            asType() >> Mock(TypeMirror)
        }
    }
}
