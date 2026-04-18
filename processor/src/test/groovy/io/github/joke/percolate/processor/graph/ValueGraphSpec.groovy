package io.github.joke.percolate.processor.graph

import com.google.testing.compile.Compilation
import com.google.testing.compile.JavaFileObjects
import io.github.joke.percolate.processor.model.ReadAccessor
import io.github.joke.percolate.processor.model.WriteAccessor
import org.jgrapht.graph.DefaultDirectedGraph
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.type.TypeMirror

import static com.google.testing.compile.Compiler.javac
import static com.google.testing.compile.CompilationSubject.assertThat

@Tag('unit')
class ValueGraphSpec extends Specification {

    // -------------------------------------------------------------------------
    // 5.1 — ValueNode package-private constructor prevents external subclassing
    // -------------------------------------------------------------------------

    @Tag('integration')
    def 'defining a ValueNode subtype outside the graph package is rejected at compile time'() {
        given:
        final source = JavaFileObjects.forSourceString('test.ExternalValueNode', '''
            import io.github.joke.percolate.processor.graph.ValueNode;
            import javax.lang.model.type.TypeMirror;

            public final class ExternalValueNode extends ValueNode {
                public TypeMirror getType() { return null; }
            }
        ''')

        expect:
        final compilation = javac().compile(source)
        assertThat(compilation).failed()
    }

    @Tag('integration')
    def 'defining a ValueEdge subtype outside the graph package is rejected at compile time'() {
        given:
        final source = JavaFileObjects.forSourceString('test.ExternalValueEdge', '''
            import io.github.joke.percolate.processor.graph.ValueEdge;

            public final class ExternalValueEdge extends ValueEdge {
            }
        ''')

        expect:
        final compilation = javac().compile(source)
        assertThat(compilation).failed()
    }

    // -------------------------------------------------------------------------
    // 5.2 — TypedValueNode equality by type string
    // -------------------------------------------------------------------------

    def 'TypedValueNode with identical type string are equal'() {
        given:
        final type1 = typeMirror('java.util.Optional<java.lang.String>')
        final type2 = typeMirror('java.util.Optional<java.lang.String>')

        when:
        final node1 = new TypedValueNode(type1, 'wrap')
        final node2 = new TypedValueNode(type2, 'wrap-2')

        then:
        node1 == node2
        node1.hashCode() == node2.hashCode()
    }

    def 'TypedValueNode with different type strings are not equal'() {
        given:
        final typeA = typeMirror('java.lang.String')
        final typeB = typeMirror('java.lang.Integer')

        expect:
        new TypedValueNode(typeA, 'label') != new TypedValueNode(typeB, 'label')
    }

    def 'TypedValueNode label does not affect equality'() {
        given:
        final type = typeMirror('java.lang.String')

        expect:
        new TypedValueNode(type, 'label-a') == new TypedValueNode(type, 'label-b')
    }

    def 'TypedValueNode equals allows JGraphT to deduplicate nodes'() {
        given:
        final graph = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge)
        final type  = typeMirror('java.lang.String')

        when:
        final node1 = new TypedValueNode(type, 'first')
        final node2 = new TypedValueNode(type, 'second')
        graph.addVertex(node1)
        graph.addVertex(node2) // same equals/hashCode → not added again

        then:
        graph.vertexSet().size() == 1
    }

    // -------------------------------------------------------------------------
    // 5.2 — PropertyNode equality by name + type string
    // -------------------------------------------------------------------------

    def 'PropertyNode with same name and type are equal'() {
        given:
        final type = typeMirror('test.Customer')
        final accessor1 = Stub(ReadAccessor)
        final accessor2 = Stub(ReadAccessor)

        expect:
        new PropertyNode('customer', type, accessor1) == new PropertyNode('customer', type, accessor2)
    }

    def 'PropertyNode with different names are not equal'() {
        given:
        final type = typeMirror('test.Customer')

        expect:
        new PropertyNode('customer', type, Stub(ReadAccessor)) !=
                new PropertyNode('address', type, Stub(ReadAccessor))
    }

    // -------------------------------------------------------------------------
    // 5.9 — graph invariants: target-slot leaves
    // -------------------------------------------------------------------------

    def 'TargetSlotNode has no outgoing edges in a well-formed ValueGraph'() {
        given:
        final graph = new DefaultDirectedGraph<ValueNode, ValueEdge>(ValueEdge)
        final paramType = typeMirror('test.Order')
        final propType  = typeMirror('java.lang.String')
        final slot      = new TargetSlotNode('name', propType, Stub(WriteAccessor))
        final source    = new SourceParamNode(Stub(javax.lang.model.element.VariableElement) {
            getSimpleName() >> Stub(javax.lang.model.element.Name) { toString() >> 'order' }
        }, paramType)

        when:
        graph.addVertex(source)
        graph.addVertex(slot)
        graph.addEdge(source, slot, new PropertyReadEdge())

        then:
        graph.outgoingEdgesOf(slot).isEmpty()
    }

    // -------------------------------------------------------------------------
    // 5.3 — LiftKind has exactly four values
    // -------------------------------------------------------------------------

    def 'LiftKind has exactly four values'() {
        expect:
        LiftKind.values().toList() == [LiftKind.NULL_CHECK, LiftKind.OPTIONAL, LiftKind.STREAM, LiftKind.COLLECTION]
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private TypeMirror typeMirror(final String name) {
        Stub(TypeMirror) { toString() >> name }
    }
}
