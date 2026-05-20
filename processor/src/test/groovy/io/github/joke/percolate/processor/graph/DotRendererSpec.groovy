package io.github.joke.percolate.processor.graph

import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.GroupCodegen
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag

@Tag('unit')
class DotRendererSpec extends Specification {

    private static final GroupCodegen NOOP_CODEGEN = { vars, inputs -> com.palantir.javapoet.CodeBlock.of('') }

    def 'renders REALISED, SEED, and MARKER edges; no SUB_SEED or ELEMENT_SEED labels'() {
        given:
        def graph = new MapperGraph()
        def scope = new HarnessScope('m()')
        def src = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('in')), scope)
        def tgt = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('out')), scope)
        graph.addNode(src)
        graph.addNode(tgt)
        graph.addEdge(Edge.seedForTest(src, tgt))
        graph.addEdge(Edge.marker(src, tgt, 'test.Strategy'))
        graph.addEdge(Edge.realised(src, tgt, 1, { _, _ -> com.palantir.javapoet.CodeBlock.of('') }, 'test.Strategy'))

        def renderer = new DotRenderer()
        def mapperType = TypeUniverse.element('java.lang.Object')

        when:
        def dot = renderer.render(graph, mapperType)

        then:
        dot.contains('SEED')
        dot.contains('MARKER')
        !dot.contains('SUB_SEED')
        !dot.contains('ELEMENT_SEED')
    }

    def 'emits a cluster block per ExpansionGroup on a MapperGraph source'() {
        given:
        def graph = new MapperGraph()
        def scope = new HarnessScope('m()')
        def root = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('')), scope)
        def slot = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('out')), scope)
        graph.addNode(root)
        graph.addNode(slot)
        def realisedEdge = Edge.realised(slot, root, 1, { _, _ -> com.palantir.javapoet.CodeBlock.of('') }, 'io.github.joke.percolate.builtin.ConstructorCall')
        graph.addEdge(realisedEdge)
        graph.addGroup(ExpansionGroup.of(root, [slot], NOOP_CODEGEN, 'io.github.joke.percolate.builtin.ConstructorCall', [realisedEdge].toSet(), graph))

        def renderer = new DotRenderer()
        def mapperType = TypeUniverse.element('java.lang.Object')

        when:
        def dot = renderer.render(graph, mapperType)

        then:
        // Group cluster block emitted
        dot.contains('subgraph "cluster_group_0"')
        // Cluster label uses the strategy's simple class name
        dot.contains('ConstructorCall #0')
        // Edges have no group= attribute (group identity is the cluster boundary)
        !dot.contains('group=')
    }

    def 'transforms view source omits group clusters because they live on MapperGraph'() {
        given:
        def graph = new MapperGraph()
        def scope = new HarnessScope('m()')
        def root = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('')), scope)
        def slot = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('out')), scope)
        graph.addNode(root)
        graph.addNode(slot)
        def realisedEdge = Edge.realised(slot, root, 1, { _, _ -> com.palantir.javapoet.CodeBlock.of('') }, 'test.Strategy')
        graph.addEdge(realisedEdge)
        graph.addGroup(ExpansionGroup.of(root, [slot], NOOP_CODEGEN, 'test.Strategy', [realisedEdge].toSet(), graph))

        def renderer = new DotRenderer()
        def mapperType = TypeUniverse.element('java.lang.Object')

        when:
        def dot = renderer.render(graph.transformsView(), mapperType)

        then:
        // No group clusters when rendering a non-MapperGraph source
        !dot.contains('cluster_group_')
    }
}
