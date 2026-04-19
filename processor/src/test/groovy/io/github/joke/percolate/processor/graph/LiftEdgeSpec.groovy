package io.github.joke.percolate.processor.graph

import com.palantir.javapoet.CodeBlock
import org.jgrapht.graph.DefaultDirectedGraph
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

@Tag('unit')
class LiftEdgeSpec extends Specification {

    // -------------------------------------------------------------------------
    // composeTemplate — inner path composed, wrapped per LiftKind
    // -------------------------------------------------------------------------

    def 'OPTIONAL lift wraps inner template in map()'() {
        given:
        final stringType = typeMirror('java.lang.String')
        final intType    = typeMirror('java.lang.Integer')

        final graph = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge)
        final innerIn  = new TypedValueNode(stringType, 'String')
        final innerOut = new TypedValueNode(intType, 'Integer')
        graph.addVertex(innerIn)
        graph.addVertex(innerOut)
        graph.addEdge(innerIn, innerOut,
                new TypeTransformEdge(null, stringType, intType,
                        { input -> CodeBlock.of('Integer.parseInt($L)', input) }))

        final lift = new LiftEdge(LiftKind.OPTIONAL, innerIn, innerOut)

        when:
        final composed = lift.composeTemplate(graph).apply(CodeBlock.of('opt'))

        then:
        composed.toString() == 'opt.map(e -> Integer.parseInt(e))'
    }

    def 'STREAM lift wraps inner template in map()'() {
        given:
        final stringType = typeMirror('java.lang.String')
        final intType    = typeMirror('java.lang.Integer')

        final graph = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge)
        final innerIn  = new TypedValueNode(stringType, 'String')
        final innerOut = new TypedValueNode(intType, 'Integer')
        graph.addVertex(innerIn)
        graph.addVertex(innerOut)
        graph.addEdge(innerIn, innerOut,
                new TypeTransformEdge(null, stringType, intType,
                        { input -> CodeBlock.of('Integer.parseInt($L)', input) }))

        final lift = new LiftEdge(LiftKind.STREAM, innerIn, innerOut)

        when:
        final composed = lift.composeTemplate(graph).apply(CodeBlock.of('stream'))

        then:
        composed.toString() == 'stream.map(e -> Integer.parseInt(e))'
    }

    def 'composeTemplate chains multiple inner edges'() {
        given:
        final aType = typeMirror('test.A')
        final bType = typeMirror('test.B')
        final cType = typeMirror('test.C')

        final graph = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge)
        final a = new TypedValueNode(aType, 'A')
        final b = new TypedValueNode(bType, 'B')
        final c = new TypedValueNode(cType, 'C')
        [a, b, c].each { graph.addVertex(it) }
        graph.addEdge(a, b,
                new TypeTransformEdge(null, aType, bType, { input -> CodeBlock.of('aToB($L)', input) }))
        graph.addEdge(b, c,
                new TypeTransformEdge(null, bType, cType, { input -> CodeBlock.of('bToC($L)', input) }))

        final lift = new LiftEdge(LiftKind.OPTIONAL, a, c)

        when:
        final composed = lift.composeTemplate(graph).apply(CodeBlock.of('val'))

        then:
        composed.toString() == 'val.map(e -> bToC(aToB(e)))'
    }

    def 'composeTemplate throws when no inner path exists'() {
        given:
        final aType = typeMirror('test.A')
        final bType = typeMirror('test.B')

        final graph = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge)
        final a = new TypedValueNode(aType, 'A')
        final b = new TypedValueNode(bType, 'B')
        graph.addVertex(a)
        graph.addVertex(b)
        // no edges — no path between a and b

        final lift = new LiftEdge(LiftKind.OPTIONAL, a, b)

        when:
        lift.composeTemplate(graph)

        then:
        thrown(IllegalStateException)
    }

    def 'composeTemplate rejects unsupported LiftKind values'() {
        given:
        final stringType = typeMirror('java.lang.String')
        final graph = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge)
        final inner = new TypedValueNode(stringType, 'String')
        graph.addVertex(inner)
        // self-edge with null transform isn't realistic but BFS returns an empty path for same-node
        final lift = new LiftEdge(kind, inner, inner)

        when:
        lift.composeTemplate(graph)

        then:
        thrown(IllegalStateException)

        where:
        kind << [LiftKind.NULL_CHECK, LiftKind.COLLECTION]
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private TypeMirror typeMirror(final String name) {
        Stub(TypeMirror) { toString() >> name }
    }
}
