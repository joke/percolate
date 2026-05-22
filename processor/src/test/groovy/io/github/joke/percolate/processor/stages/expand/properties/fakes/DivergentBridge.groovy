package io.github.joke.percolate.processor.stages.expand.properties.fakes

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.spi.*
import io.github.joke.percolate.spi.test.TypeUniverse

import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

final class DivergentBridge implements Bridge {

    private static final EdgeCodegen NO_OP_CODEGEN = { vars, inputs -> CodeBlock.of('') }

    @Override
    Stream<BridgeStep> bridge(final TypeMirror from, final TypeMirror to, final ResolveCtx ctx) {
        // The bridge advertises an input type that always differs from the candidate's type,
        // so commitBridgeStep allocates a fresh phantom node every round. The fresh chain
        // never connects to a source parameter root, so slotReachable stays false and the
        // per-slot round cap (MAX_SLOT_ROUNDS) trips with `did not converge`.
        final inputType = ctx.types().isSameType(from, TypeUniverse.STRING)
                ? TypeUniverse.LONG_TYPE
                : TypeUniverse.STRING
        Stream.of(new BridgeStep(inputType, to, 1, NO_OP_CODEGEN))
    }
}
