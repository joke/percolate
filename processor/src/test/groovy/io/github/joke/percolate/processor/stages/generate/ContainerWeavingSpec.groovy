package io.github.joke.percolate.processor.stages.generate

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.MapperContext
import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.model.MapperShape
import io.github.joke.percolate.spi.ContainerCodegen
import io.github.joke.percolate.spi.EdgeCodegen
import io.github.joke.percolate.spi.GroupCodegen
import io.github.joke.percolate.spi.Nullability
import io.github.joke.percolate.spi.ElementScope
import io.github.joke.percolate.spi.WrapperCodegen
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror

/**
 * Exercises the composer's container weaving over hand-built single-edge PlanView chains. The container handles
 * are stubs so the test pins the weaving structure (open/flat-map/map/collect/unwrap, isStream threading), not
 * any built-in's syntax.
 */
@Tag('unit')
class ContainerWeavingSpec extends Specification {

    static final EdgeCodegen MAP_X = { vars, inputs -> CodeBlock.of('mapX($L)', inputs.single()) } as EdgeCodegen

    static final ContainerCodegen SEQ = new ContainerCodegen() {
        CodeBlock iterate(CodeBlock c) { CodeBlock.of('$L.stream()', c) }
        CodeBlock mapElements(CodeBlock s, String v, CodeBlock b) { CodeBlock.of('$L.map($N -> $L)', s, v, b) }
        CodeBlock flatMapElements(CodeBlock s, String v, CodeBlock i) { CodeBlock.of('$L.flatMap($N -> $L)', s, v, i) }
        CodeBlock collect(CodeBlock s) { CodeBlock.of('$L.collect(toList())', s) }
    }

    static final WrapperCodegen WRAP = new WrapperCodegen() {
        CodeBlock iterate(CodeBlock c) { CodeBlock.of('$L.stream()', c) }
        CodeBlock mapElements(CodeBlock s, String v, CodeBlock b) { CodeBlock.of('$L.map($N -> $L)', s, v, b) }
        CodeBlock flatMapElements(CodeBlock s, String v, CodeBlock i) { CodeBlock.of('$L.flatMap($N -> $L)', s, v, i) }
        CodeBlock mapPresence(CodeBlock w, String v, CodeBlock b) { CodeBlock.of('$L.map($N -> $L)', w, v, b) }
        CodeBlock wrap(CodeBlock x) { CodeBlock.of('opt($L)', x) }
        CodeBlock unwrap(CodeBlock w, Nullability n) {
            n == Nullability.NULLABLE ? CodeBlock.of('$L.orElse(null)', w) : CodeBlock.of('$L.orElseThrow()', w)
        }
    }

    def 'sequence target weaves iterate -> map -> collect'() {
        given:
        def method = mockMethod('map', [mockParam('p')])
        def scope = new MethodScope(method)
        def graph = new MapperGraph()
        def src = node(scope, sourceLoc('p'))
        def elemIn = node(scope, new ElementLocation())
        def elemOut = node(scope, new ElementLocation())
        def root = node(scope, returnRootLoc())
        [src, elemIn, elemOut, root].each { graph.addNode(it) }
        graph.addEdge(Edge.realised(src, elemIn, Weights.CONTAINER, SEQ, ElementScope.ENTERING, 'Seq'))
        graph.addEdge(Edge.realised(elemIn, elemOut, Weights.STEP, MAP_X, 'MapX'))
        graph.addEdge(Edge.realised(elemOut, root, Weights.CONTAINER, SEQ, ElementScope.EXITING, 'Seq'))

        expect:
        body(graph, method) == 'return p.stream().map(v0 -> mapX(v0)).collect(toList());'
    }

    def 'top-level wrapper unwraps under the target nullability'() {
        given:
        def method = mockMethod('map', [mockParam('o')])
        def scope = new MethodScope(method)
        def graph = new MapperGraph()
        def src = node(scope, sourceLoc('o'))
        def root = typedNode(scope, returnRootLoc(), nullability)
        [src, root].each { graph.addNode(it) }
        graph.addEdge(Edge.realised(src, root, Weights.CONTAINER, WRAP, ElementScope.ENTERING, 'Opt'))

        expect:
        body(graph, method) == expected

        where:
        nullability            | expected
        Nullability.NON_NULL   | 'return o.orElseThrow();'
        Nullability.NULLABLE   | 'return o.orElse(null);'
    }

    def 'wrapper inside a sequence flat-maps (FilterPresent) then maps and collects'() {
        given:
        def method = mockMethod('map', [mockParam('p')])
        def scope = new MethodScope(method)
        def graph = new MapperGraph()
        def src = node(scope, sourceLoc('p'))
        def elemOpt = node(scope, new ElementLocation())
        def elemX = node(scope, new ElementLocation())
        def elemY = node(scope, new ElementLocation())
        def root = node(scope, returnRootLoc())
        [src, elemOpt, elemX, elemY, root].each { graph.addNode(it) }
        graph.addEdge(Edge.realised(src, elemOpt, Weights.CONTAINER, SEQ, ElementScope.ENTERING, 'Seq'))
        graph.addEdge(Edge.realised(elemOpt, elemX, Weights.CONTAINER, WRAP, ElementScope.ENTERING, 'Opt'))
        graph.addEdge(Edge.realised(elemX, elemY, Weights.STEP, MAP_X, 'MapX'))
        graph.addEdge(Edge.realised(elemY, root, Weights.CONTAINER, SEQ, ElementScope.EXITING, 'Seq'))

        expect:
        body(graph, method) ==
                'return p.stream().flatMap(v0 -> v0.stream()).map(v1 -> mapX(v1)).collect(toList());'
    }

    def '@Nullable List<@Nullable X>: per-level nullability does not disturb the sequence weave'() {
        given:
        def method = mockMethod('map', [mockParam('p')])
        def scope = new MethodScope(method)
        def graph = new MapperGraph()
        def src = node(scope, sourceLoc('p'))
        def elemIn = typedNode(scope, new ElementLocation(), Nullability.NULLABLE)
        def elemOut = node(scope, new ElementLocation())
        def root = typedNode(scope, returnRootLoc(), Nullability.NULLABLE)
        [src, elemIn, elemOut, root].each { graph.addNode(it) }
        graph.addEdge(Edge.realised(src, elemIn, Weights.CONTAINER, SEQ, ElementScope.ENTERING, 'Seq'))
        graph.addEdge(Edge.realised(elemIn, elemOut, Weights.STEP, MAP_X, 'MapX'))
        graph.addEdge(Edge.realised(elemOut, root, Weights.CONTAINER, SEQ, ElementScope.EXITING, 'Seq'))

        expect:
        body(graph, method) == 'return p.stream().map(v0 -> mapX(v0)).collect(toList());'
    }

    def 'a single-slot scalar group fed by a stream maps per element (does not drop isStream)'() {
        given:
        // A scalar bridge (e.g. a conversion / method call) registers a single-slot group. When its slot renders
        // as an open stream, the group codegen must be applied per element, not to the stream as a whole.
        def method = mockMethod('map', [mockParam('p')])
        def scope = new MethodScope(method)
        def graph = new MapperGraph()
        def src = node(scope, sourceLoc('p'))
        def elem = node(scope, new ElementLocation())
        def root = node(scope, returnRootLoc())
        [src, elem, root].each { graph.addNode(it) }
        graph.addEdge(Edge.realised(src, elem, Weights.CONTAINER, SEQ, ElementScope.ENTERING, 'Seq'))
        def convEdge = Edge.realised(elem, root, Weights.STEP, MAP_X, 'Conv')
        graph.addEdge(convEdge)

        GroupCodegen conv = { vars, inputs -> CodeBlock.of('conv($L)', inputs.single()) } as GroupCodegen
        def group = ExpansionGroup.of(root, [elem], conv, 'Conv', [convEdge] as Set, graph)
        graph.addGroup(group)
        graph.recordGroupOutcome(GroupOutcome.sat(group))

        expect:
        body(graph, method) == 'return p.stream().map(v0 -> conv(v0));'
    }

    def '@Nullable Optional<@Nullable X>: presence collapse reads nullability, distinct from the inner ref'() {
        given:
        def method = mockMethod('map', [mockParam('o')])
        def scope = new MethodScope(method)
        def graph = new MapperGraph()
        def src = node(scope, sourceLoc('o'))
        def root = typedNode(scope, returnRootLoc(), Nullability.NULLABLE)
        [src, root].each { graph.addNode(it) }
        graph.addEdge(Edge.realised(src, root, Weights.CONTAINER, WRAP, ElementScope.ENTERING, 'Opt'))

        expect:
        body(graph, method) == 'return o.orElse(null);'
    }

    private static String body(final MapperGraph graph, final ExecutableElement method) {
        def ctx = new MapperContext(null)
        ctx.shape = new MapperShape(null, List.of(method))
        ctx.graph = graph
        new BuildMethodBodies().build(ctx)[0].body.toString().trim()
    }

    private Node node(final Scope scope, final Location loc) {
        new Node(Optional.of(TypeUniverse.STRING), loc, scope)
    }

    private Node typedNode(final Scope scope, final Location loc, final Nullability nullability) {
        def n = new Node(Optional.empty(), loc, scope)
        n.setTyping(TypeUniverse.STRING, nullability)
        n
    }

    private SourceLocation sourceLoc(final String... segments) {
        new SourceLocation(new AccessPath(segments as List))
    }

    private TargetLocation returnRootLoc() {
        new TargetLocation(TargetPath.of(''))
    }

    private ExecutableElement mockMethod(final String name, final List<VariableElement> params) {
        def m = Mock(ExecutableElement)
        m.simpleName >> nameOf(name)
        m.parameters >> params
        m.returnType >> TypeUniverse.STRING
        m.thrownTypes >> []
        m
    }

    private VariableElement mockParam(final String name) {
        def p = Mock(VariableElement)
        p.simpleName >> nameOf(name)
        def typeMock = Mock(TypeMirror)
        typeMock.toString() >> 'java.lang.Object'
        p.asType() >> typeMock
        p
    }

    private Name nameOf(final String value) {
        def n = Mock(Name)
        n.toString() >> value
        n
    }
}
