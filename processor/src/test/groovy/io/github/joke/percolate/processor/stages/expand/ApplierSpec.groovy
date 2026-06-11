package io.github.joke.percolate.processor.stages.expand


import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.nullability.JspecifyNullabilityResolver
import io.github.joke.percolate.processor.nullability.NullabilityAnnotations
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.EdgeCodegen
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Tag

@Tag('unit')
class ApplierSpec extends Specification {

    private static final EdgeCodegen EDGE_NOOP = { vars, inputs -> CodeBlock.of('') }

    def graph = new MapperGraph()
    def scope = new HarnessScope('m()')
    def resolver = new JspecifyNullabilityResolver(NullabilityAnnotations.jspecifyDefaults(), TypeUniverse.elements())
    @Subject
    Applier applier = new Applier(resolver)
    def state = new ExpansionStateImpl(graph, applier)

    def 'accepted bundle applies every delta and builds the nested group'() {
        given:
        def frontier = source('f', TypeUniverse.STRING)
        def input = source('i', TypeUniverse.STRING)
        graph.addNode(frontier)
        def edge = Edge.realised(1, EDGE_NOOP, 'test.Bridge')
        def bundle = new DeltaBundle('test.Bridge', [
                new AddNode(input),
                new AddEdge(input, frontier, edge),
                new AddGroup(frontier, [input], [], false),
        ])

        when:
        def applied = applier.apply(state, [bundle])

        then:
        applied == 3
        graph.nodes().anyMatch { it.is(input) }
        graph.edges().anyMatch { it.is(edge) }
        graph.groups().count() == 1
    }

    def 'cycle-rejected bundle is dropped whole and leaves no orphan node'() {
        given:
        def a = source('a', TypeUniverse.STRING)
        def b = target('b', TypeUniverse.STRING)
        graph.addNode(a)
        graph.addNode(b)
        graph.addEdge(a, b, Edge.realised(1, EDGE_NOOP, 'test.Forward'))
        def orphan = source('orphan', TypeUniverse.STRING)
        def backEdge = Edge.realised(1, EDGE_NOOP, 'test.Back')
        def bundle = new DeltaBundle('test.Back', [new AddNode(orphan), new AddEdge(b, a, backEdge)])

        when:
        def applied = applier.apply(state, [bundle])

        then:
        applied == 0
        !graph.nodes().anyMatch { it.is(orphan) }
        !graph.edges().anyMatch { it.is(backEdge) }
    }

    def 'TypeNode types an untyped node and records its producer scope'() {
        given:
        def node = new Node(Optional.empty(), new SourceLocation(AccessPath.of('u')), scope)
        graph.addNode(node)
        def producer = TypeUniverse.anyConstruct() as javax.lang.model.element.Element

        when:
        applier.apply(state, [new DeltaBundle('test', [new TypeNode(node, TypeUniverse.STRING, producer)])])

        then:
        node.type.present
        TypeUniverse.types().isSameType(node.type.get(), TypeUniverse.STRING)
        applier.hasProducerScope(node)
        applier.producerScope(node).is(producer)
    }

    def 'TypeNode on an already-typed node is a no-op'() {
        given:
        def node = source('t', TypeUniverse.STRING)
        graph.addNode(node)

        when:
        applier.apply(state, [new DeltaBundle('test', [new TypeNode(node, TypeUniverse.LONG, null)])])

        then:
        noExceptionThrown()
        TypeUniverse.types().isSameType(node.type.get(), TypeUniverse.STRING)
    }

    def 'an AddNode for a CONVERSION input stamps the in-effect directive onto the synthesized node'() {
        given: 'a frontier carries directive D; the driver synthesizes its CONVERSION input carrying D'
        def directive = new SegmentDirective('street')
        def synthesized = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('s')), scope)

        when:
        applier.apply(state, [new DeltaBundle('test.Conversion', [new AddNode(synthesized, directive, null)])])

        then: 'the node inherits D, so the Frontier later built for it returns D from directive()'
        synthesized.directive.present
        synthesized.directive.get().is(directive)
        new FrontierContext(TypeUniverse.STRING, synthesized.directive, []).directive().get().is(directive)
    }

    def 'an AddNode for a BOUNDARY slot inherits no directive'() {
        given: 'a boundary slot crosses to a new value and must not inherit the parent directive'
        def slot = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('s')), scope)

        when:
        applier.apply(state, [new DeltaBundle('test.Boundary', [new AddNode(slot)])])

        then:
        slot.directive.empty
    }

    private Node source(final String path, final javax.lang.model.type.TypeMirror type) {
        new Node(Optional.of(type), new SourceLocation(AccessPath.of(path)), scope)
    }

    private Node target(final String path, final javax.lang.model.type.TypeMirror type) {
        new Node(Optional.of(type), new TargetLocation(TargetPath.of(path)), scope)
    }
}
