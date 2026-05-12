package io.github.joke.percolate.processor.stages.expand

import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.spi.Bridge
import io.github.joke.percolate.processor.spi.BridgeStep
import io.github.joke.percolate.processor.spi.ElementSeed
import io.github.joke.percolate.processor.spi.ResolveCtx
import io.github.joke.percolate.processor.spi.Weights
import spock.lang.Specification
import spock.lang.Tag

import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Name
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Types
import java.util.stream.Stream

@Tag('unit')
class BridgeSourceToTargetPhaseExtendedSpec extends Specification {

    def 'element-scope SEED is processed by the phase'() {
        given:
        def graph = new MapperGraph()
        def scope = new MethodScope(mockMethod('map'))
        def dogType = mockTypeMirror('Dog')
        def petType = mockTypeMirror('Pet')

        def parent = new Node(Optional.of(dogType), new SourceLocation(mockAccessPath('xs')), scope, Optional.empty())
        def eFrom = new Node(Optional.of(dogType), new ElementLocation(), scope, Optional.of(parent))
        def eTo = new Node(Optional.of(petType), new ElementLocation(), scope, Optional.of(parent))
        graph.addNode(parent)
        graph.addNode(eFrom)
        graph.addNode(eTo)
        graph.addEdge(Edge.elementSeed(eFrom, eTo, 'test.Strategy'))

        def codegen = { vars, inputs -> null } as io.github.joke.percolate.processor.spi.EdgeCodegen
        def bridge = Mock(Bridge)
        bridge.bridge(_, _, _) >> Stream.of(new BridgeStep(dogType, petType, Weights.STEP, codegen, List.of()))
        def types = Mock(Types)
        types.isSameType(_, _) >> { a, b -> a == b }
        def ctx = Mock(ResolveCtx)
        ctx.types() >> types

        when:
        def phase = new BridgeSourceToTargetPhase([bridge], ctx)
        phase.apply(graph)

        then:
        graph.edges().any { it.kind == EdgeKind.REALISED }
    }

    def 'element-seed emission creates parent-linked element nodes and SEED edge'() {
        given:
        def graph = new MapperGraph()
        def scope = new MethodScope(mockMethod('map'))
        def dogType = mockTypeMirror('Dog')
        def petType = mockTypeMirror('Pet')

        def f = new Node(Optional.of(dogType), new SourceLocation(mockAccessPath('xs')), scope, Optional.empty())
        def t = new Node(Optional.of(petType), new TargetLocation(mockTargetPath('')), scope, Optional.empty())
        graph.addNode(f)
        graph.addNode(t)
        def directive = Mock(javax.lang.model.element.AnnotationMirror)
        graph.addEdge(Edge.seed(f, t, directive))

        def codegen = { vars, inputs -> null } as io.github.joke.percolate.processor.spi.EdgeCodegen
        def bridge = Mock(Bridge)
        def innerFrom = mockTypeMirror('Dog')
        def innerTo = mockTypeMirror('Pet')
        def step = new BridgeStep(dogType, petType, Weights.CONTAINER, codegen,
                List.of(new ElementSeed('element', innerFrom, innerTo)))

        def types = Mock(Types)
        types.isSameType(_, _) >> { a, b -> a == b }
        def ctx = Mock(ResolveCtx)
        ctx.types() >> types

        when:
        bridge.bridge(_, _, _) >> Stream.of(step)
        def phase = new BridgeSourceToTargetPhase([bridge], ctx)
        phase.apply(graph)

        then:
        graph.nodes().count() >= 4
        graph.edges().any { it.kind == EdgeKind.SEED && it.from.loc instanceof ElementLocation }
    }

    def 'output-side SUB_SEED is emitted when outputNode differs from T'() {
        given:
        def graph = new MapperGraph()
        def scope = new MethodScope(mockMethod('map'))
        def dogType = mockTypeMirror('Dog')
        def petType = mockTypeMirror('Pet')
        def animalType = mockTypeMirror('Animal')

        def f = new Node(Optional.of(dogType), new SourceLocation(mockAccessPath('xs')), scope, Optional.empty())
        def t = new Node(Optional.of(petType), new TargetLocation(mockTargetPath('')), scope, Optional.empty())
        graph.addNode(f)
        graph.addNode(t)
        def directive = Mock(javax.lang.model.element.AnnotationMirror)
        graph.addEdge(Edge.seed(f, t, directive))

        def codegen = { vars, inputs -> null } as io.github.joke.percolate.processor.spi.EdgeCodegen
        def bridge = Mock(Bridge)
        def step = new BridgeStep(dogType, animalType, Weights.STEP, codegen, List.of())

        def types = Mock(Types)
        types.isSameType(_, _) >> { a, b -> a == b }
        def ctx = Mock(ResolveCtx)
        ctx.types() >> types

        when:
        bridge.bridge(_, _, _) >> Stream.of(step)
        def phase = new BridgeSourceToTargetPhase([bridge], ctx)
        phase.apply(graph)

        then:
        def subSeeds = graph.edges().filter { it.kind == EdgeKind.SUB_SEED }.toList()
        subSeeds.size() == 1
        subSeeds[0].to == t
    }

    private TypeMirror mockTypeMirror(String typeName) {
        def tm = Mock(TypeMirror)
        tm.toString() >> typeName
        tm
    }

    private AccessPath mockAccessPath(String path) {
        new AccessPath(path.split('\\.').toList())
    }

    private TargetPath mockTargetPath(String path) {
        new TargetPath(path.isEmpty() ? [] : path.split('\\.').toList())
    }

    private ExecutableElement mockMethod(String name) {
        def m = Mock(ExecutableElement)
        def n = Mock(Name)
        n.toString() >> name
        m.getSimpleName() >> n
        m.parameters >> []
        def retType = Mock(TypeMirror)
        retType.toString() >> 'Human'
        m.getReturnType() >> retType
        m
    }
}
