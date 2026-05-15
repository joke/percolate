package io.github.joke.percolate.processor.stages.expand.properties.fakes

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.Bridge
import io.github.joke.percolate.spi.BridgeStep
import io.github.joke.percolate.spi.EdgeCodegen
import io.github.joke.percolate.spi.ElementSeed
import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.test.TypeUniverse

import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

final class IdentityBridge implements Bridge {

    private static final EdgeCodegen NO_OP_CODEGEN = { vars, inputs -> CodeBlock.of('') }

    private final TypeMirror inType
    private final TypeMirror outType

    IdentityBridge(final TypeMirror inType, final TypeMirror outType) {
        this.inType = Objects.requireNonNull(inType)
        this.outType = Objects.requireNonNull(outType)
    }

    @Override
    Stream<BridgeStep> bridge(final TypeMirror from, final TypeMirror to, final ResolveCtx ctx) {
        if (TypeUniverse.types().isSameType(from, inType) && TypeUniverse.types().isSameType(to, outType)) {
            final elementSeed = new ElementSeed('identity', inType, outType)
            return Stream.of(new BridgeStep(inType, outType, 1, NO_OP_CODEGEN, [elementSeed]))
        }
        Stream.empty()
    }
}
