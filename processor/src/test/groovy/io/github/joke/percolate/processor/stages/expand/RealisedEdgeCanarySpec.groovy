package io.github.joke.percolate.processor.stages.expand

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.graph.EdgeKind
import io.github.joke.percolate.processor.test.ExpansionHarness
import io.github.joke.percolate.processor.test.GraphFixtures
import io.github.joke.percolate.spi.Bridge
import io.github.joke.percolate.spi.BridgeStep
import io.github.joke.percolate.spi.EdgeCodegen
import io.github.joke.percolate.spi.test.TypeUniverse
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.Timeout

import java.util.stream.Stream

@Tag('unit')
@Timeout(30)
class RealisedEdgeCanarySpec extends Specification {

    private static final EdgeCodegen NO_OP_CODEGEN = { vars, inputs -> CodeBlock.of('') }

    def 'identity bridge produces realised edge'() {
        given:
        final seed = GraphFixtures.graphWithSeedAndRealisedPath()
        final bridge = identityBridge(TypeUniverse.STRING, TypeUniverse.STRING)

        when:
        final result = ExpansionHarness.expand(seed, [bridge], [])

        then:
        result.expandedGraph().edges().any { it.kind == EdgeKind.REALISED }
    }

    private static Bridge identityBridge(inType, outType) {
        { from, to, ctx ->
            if (TypeUniverse.types().isSameType(from, inType) && TypeUniverse.types().isSameType(to, outType)) {
                Stream.of(new BridgeStep(inType, outType, 1, NO_OP_CODEGEN))
            } else {
                Stream.empty()
            }
        } as Bridge
    }
}
