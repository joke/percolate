package io.github.joke.percolate.processor.stages.expand

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.EdgeKind
import io.github.joke.percolate.processor.test.ExpansionHarness
import io.github.joke.percolate.processor.test.GraphFixtures
import io.github.joke.percolate.spi.CombinatorialMatch
import io.github.joke.percolate.spi.EdgeCodegen
import io.github.joke.percolate.spi.ExpansionStep
import io.github.joke.percolate.spi.Slot
import io.github.joke.percolate.spi.Weights
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Timeout

import java.util.stream.Stream

@Tag('unit')
@Timeout(30)
class RealisedEdgeCanarySpec extends Specification {

    private static final EdgeCodegen NO_OP_CODEGEN = { vars, inputs -> CodeBlock.of('') }

    def 'identity strategy produces a realised edge'() {
        given:
        final seed = GraphFixtures.graphWithSeedAndRealisedPath()
        final strategy = identityStrategy(TypeUniverse.STRING, TypeUniverse.STRING)

        when:
        final result = ExpansionHarness.expand(seed, [strategy])

        then:
        result.expandedGraph().edges().any { it.kind == EdgeKind.REALISED }
    }

    private static CombinatorialMatch identityStrategy(inType, outType) {
        { from, to, ctx ->
            if (TypeUniverse.types().isSameType(from, inType) && TypeUniverse.types().isSameType(to, outType)) {
                Stream.of(ExpansionStep.conversion(new Slot('value', inType, Weights.NOOP, null), outType, NO_OP_CODEGEN, Weights.NOOP))
            } else {
                Stream.empty()
            }
        } as CombinatorialMatch
    }
}
