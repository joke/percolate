package io.github.joke.percolate.processor.graph


import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class MapperGraphAppendOnlySpec extends Specification {

    def 'MapperGraph exposes no node or edge removal methods'() {
        expect:
        !hasRemovalMethod(MapperGraph, 'removeNode')
        !hasRemovalMethod(MapperGraph, 'removeEdge')
        !hasRemovalMethod(MapperGraph, 'remove')
        !hasRemovalMethod(MapperGraph, 'clear')
        !hasRemovalMethod(MapperGraph, 'delete')
        !hasRemovalMethod(MapperGraph, 'evict')
    }

    def 'SEED edges survive into the post-expansion graph'() {
        given:
        final var graph = new MapperGraph()
        final var scope = new HarnessScope('test()')
        final var source = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('in')), scope)
        final var target = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('out')), scope)
        final var realised = new Node(Optional.of(TypeUniverse.STRING), new ElementLocation('realised'), scope)
        graph.addNode(source)
        graph.addNode(target)
        graph.addNode(realised)
        graph.addEdge(source, target, Edge.seedForTest())
        final var seedCountBefore = graph.edges().filter { it.kind == EdgeKind.SEED }.count()

        when:
        // Simulate expansion by adding a realised edge
        graph.addEdge(realised, target, Edge.realised(1, { _, _ -> com.palantir.javapoet.CodeBlock.of('') }, 'test.Strategy'))

        then:
        graph.edges().filter { it.kind == EdgeKind.SEED }.count() == seedCountBefore
    }

    def 'adding nodes and edges never removes existing ones'() {
        given:
        final var graph = new MapperGraph()
        final var scope = new HarnessScope('test()')
        final var n1 = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('a')), scope) 
        final var n2 = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('b')), scope) 
        final var n3 = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('c')), scope) 
        graph.addNode(n1)
        graph.addNode(n2)
        graph.addEdge(n1, n2, Edge.seedForTest())
        final var nodesBefore = graph.nodeCount()
        final var edgesBefore = graph.edgeCount()

        when:
        graph.addNode(n3)
        graph.addEdge(n2, n3, Edge.seedForTest())

        then:
        graph.nodeCount() == nodesBefore + 1
        graph.edgeCount() == edgesBefore + 1
    }

    private static boolean hasRemovalMethod(final Class<?> clazz, final String methodName) {
        try {
            clazz.getMethod(methodName, Node)
            return true
        } catch (final NoSuchMethodException e) {
            try {
                clazz.getMethod(methodName, Edge)
                return true
            } catch (final NoSuchMethodException e2) {
                try {
                    clazz.getMethod(methodName, Object)
                    return true
                } catch (final NoSuchMethodException e3) {
                    return false
                }
            }
        }
    }

    private static final class HarnessScope implements Scope {
        private final String name
        HarnessScope(final String name) { this.name = name }
        @Override String encode() { name }
    }
}
