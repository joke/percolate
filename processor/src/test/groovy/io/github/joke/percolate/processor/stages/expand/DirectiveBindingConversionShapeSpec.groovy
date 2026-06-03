package io.github.joke.percolate.processor.stages.expand

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.*
import io.github.joke.percolate.processor.test.ExpansionHarness
import io.github.joke.percolate.spi.CombinatorialMatch
import io.github.joke.percolate.spi.EdgeCodegen
import io.github.joke.percolate.spi.ExpansionStep
import io.github.joke.percolate.spi.GroupCodegen
import io.github.joke.percolate.spi.Slot
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Timeout

import javax.lang.model.element.ExecutableElement
import java.util.stream.Stream

/**
 * A directive binding whose declared target type differs from its source type must realise a CONVERSION into the
 * target leaf — folding a re-typing edge from the source — rather than a same-type direct assignment. The leaf's
 * declared type is pinned by the consuming assembly (here via {@link ExpansionGroup#recordExpectedType}, the role
 * the {@code Applier} fills when a constructor binds the leaf).
 */
@Tag('unit')
@Timeout(30)
class DirectiveBindingConversionShapeSpec extends Specification {

    private static final String SEED_FQN = 'io.github.joke.percolate.processor.stages.seed.SeedGraph'
    private static final GroupCodegen GROUP_NOOP = { vars, inputs -> CodeBlock.of('') }
    private static final EdgeCodegen EDGE_NOOP = { vars, inputs -> CodeBlock.of('') }

    def 'a directive binding to a wider-typed leaf folds a conversion edge, not a source-typed direct assign'() {
        given:
        def graph = new MapperGraph()
        def methodScope = new MethodScope(singleParamMethod())
        def paramName = singleParamMethod().parameters[0].simpleName.toString()
        def leaf = new Node(Optional.empty(), new TargetLocation(TargetPath.of('x')), methodScope)
        def source = new Node(Optional.of(TypeUniverse.STRING), new SourceLocation(AccessPath.of(paramName)), methodScope)
        [leaf, source].each { graph.addNode(it) }
        graph.addEdge(Edge.seedForTest(source, leaf))
        // SeedGraph would register this directive-binding group (root = target leaf, slot = source).
        def binding = ExpansionGroup.of(leaf, [source], GROUP_NOOP, SEED_FQN, [].toSet(), graph)
        graph.addGroup(binding)
        // The consuming assembly pins the leaf's declared type as LONG (wider than the STRING source).
        binding.recordExpectedType(leaf, new Slot('x', TypeUniverse.LONG_TYPE, Weights.STEP, null))

        when:
        def result = ExpansionHarness.expand(graph, [stringToLong()])

        then:
        def g = result.expandedGraph()
        def tgtX = g.nodes().filter { it.loc instanceof TargetLocation && it.loc.path.segments == ['x'] }
                .findFirst().get()

        and: 'the target leaf carries its declared LONG type, not the STRING source type'
        tgtX.type.present
        TypeUniverse.types().isSameType(tgtX.type.get(), TypeUniverse.LONG_TYPE)
        !TypeUniverse.types().isSameType(tgtX.type.get(), TypeUniverse.STRING)

        and: 'a realised edge reaches the leaf, folded from the STRING source (a conversion, not a same-type assign)'
        def into = g.edges().filter { it.kind == EdgeKind.REALISED && it.to.is(tgtX) }.toList()
        !into.empty
        into.every { TypeUniverse.types().isSameType(it.from.type.get(), TypeUniverse.STRING) }
    }

    private static ExecutableElement singleParamMethod() {
        TypeUniverse.element('java.lang.String').enclosedElements.stream()
                .filter { it instanceof ExecutableElement }
                .map { it as ExecutableElement }
                .filter { it.simpleName.toString() == 'concat' && it.parameters.size() == 1 }
                .findFirst()
                .orElseThrow { new IllegalStateException('String.concat(String) not found') }
    }

    private static CombinatorialMatch stringToLong() {
        { from, to, c ->
            TypeUniverse.types().isSameType(from, TypeUniverse.STRING) && TypeUniverse.types().isSameType(to, TypeUniverse.LONG_TYPE)
                    ? Stream.of(ExpansionStep.conversion(new Slot('v', TypeUniverse.STRING, Weights.STEP, null), TypeUniverse.LONG_TYPE, EDGE_NOOP, Weights.STEP))
                    : Stream.empty()
        } as CombinatorialMatch
    }
}
