package io.github.joke.percolate.processor.stages.expand

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.test.ExpansionHarness
import io.github.joke.percolate.processor.test.HarnessScope
import io.github.joke.percolate.spi.*
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Timeout

import java.util.stream.Stream

@Tag('unit')
@Timeout(30)
class DirectiveBindingConversionShapeSpec extends Specification {

    private static final String DIRECTIVE_BINDING_FQN =
            'io.github.joke.percolate.processor.stages.expand.DirectiveBinding'
    private static final GroupCodegen GROUP_NOOP = { vars, inputs -> CodeBlock.of('') }
    private static final EdgeCodegen EDGE_NOOP = { vars, inputs -> CodeBlock.of('') }

    def 'a directive binding whose target type differs from the source realises a conversion edge, not a source-typed direct assign'() {
        given:
        def graph = new MapperGraph()
        def scope = new HarnessScope('mapHuman()')
        def returnRoot = new Node(Optional.of(TypeUniverse.STRING), new TargetLocation(TargetPath.of('')), scope)
        def leaf = new Node(Optional.empty(), new TargetLocation(TargetPath.of('x')), scope)
        def source = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of('p')), scope)
        [returnRoot, leaf, source].each { graph.addNode(it) }
        graph.addEdge(Edge.seedForTest(leaf, returnRoot))
        graph.addEdge(Edge.seedForTest(source, leaf))
        // SeedGraph would register this directive-binding group (root = target leaf, slot = source)
        def binding = ExpansionGroup.of(leaf, [source], GROUP_NOOP, DIRECTIVE_BINDING_FQN, [].toSet(), graph)
        graph.addGroup(binding)
        // Target chain declares the leaf's type as LONG (differs from the STRING source)
        def groupTarget = { returnType, tails, c ->
            Optional.of(new GroupBuild([new Slot('x', TypeUniverse.LONG_TYPE, 1, TypeUniverse.anyConstruct())], GROUP_NOOP))
        } as GroupTarget
        def bridge = { from, to, c ->
            TypeUniverse.types().isSameType(from, TypeUniverse.STRING)
                    && TypeUniverse.types().isSameType(to, TypeUniverse.LONG_TYPE)
                    ? Stream.of(new BridgeStep(TypeUniverse.STRING, TypeUniverse.LONG_TYPE, 1, EDGE_NOOP))
                    : Stream.empty()
        } as Bridge

        when:
        def result = ExpansionHarness.expand(graph, [bridge], [groupTarget])

        then:
        def g = result.expandedGraph()
        def tgtX = g.nodes().filter { it.loc instanceof TargetLocation && it.loc.path.segments == ['x'] }
                .findFirst().get()

        and: 'the target leaf carries its declared LONG type, not the STRING source type'
        tgtX.type.present
        TypeUniverse.types().isSameType(tgtX.type.get(), TypeUniverse.LONG_TYPE)

        and: 'no source-typed direct-assign edge was emitted into the target leaf'
        g.edges().filter {
            it.kind == EdgeKind.REALISED && it.to.is(tgtX) && it.strategyClassFqn == DIRECTIVE_BINDING_FQN
        }.toList().empty

        and: 'a realised conversion edge reaches the target leaf'
        !g.edges().filter { it.kind == EdgeKind.REALISED && it.to.is(tgtX) }.toList().empty
    }
}
