package io.github.joke.percolate.processor.stages.expand.properties.fakes

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.spi.Bridge
import io.github.joke.percolate.processor.spi.BridgeStep
import io.github.joke.percolate.processor.spi.EdgeCodegen
import io.github.joke.percolate.processor.spi.ElementSeed
import io.github.joke.percolate.processor.spi.ResolveCtx
import io.github.joke.percolate.processor.test.TypeUniverse

import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

final class ChainBridge implements Bridge {

    private static final EdgeCodegen NO_OP_CODEGEN = { vars, inputs -> CodeBlock.of('') }

    private final TypeMirror inType
    private final TypeMirror midType
    private final TypeMirror outType

    ChainBridge(final TypeMirror inType, final TypeMirror midType, final TypeMirror outType) {
        this.inType = Objects.requireNonNull(inType)
        this.midType = Objects.requireNonNull(midType)
        this.outType = Objects.requireNonNull(outType)
    }

    @Override
    Stream<BridgeStep> bridge(final TypeMirror from, final TypeMirror to, final ResolveCtx ctx) {
        if (TypeUniverse.types().isSameType(from, inType) && TypeUniverse.types().isSameType(to, outType)) {
            final step1 = new ElementSeed('chain-1', inType, midType)
            final step2 = new ElementSeed('chain-2', midType, outType)
            return Stream.of(
                    new BridgeStep(inType, midType, 1, NO_OP_CODEGEN, [step1]),
                    new BridgeStep(midType, outType, 1, NO_OP_CODEGEN, [step2]))
        }
        Stream.empty()
    }
}
