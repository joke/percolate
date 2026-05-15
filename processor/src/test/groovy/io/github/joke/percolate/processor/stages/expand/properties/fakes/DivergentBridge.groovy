package io.github.joke.percolate.processor.stages.expand.properties.fakes

import com.palantir.javapoet.CodeBlock
import io.github.joke.percolate.processor.spi.Bridge
import io.github.joke.percolate.processor.spi.BridgeStep
import io.github.joke.percolate.processor.spi.EdgeCodegen
import io.github.joke.percolate.processor.spi.ElementSeed
import io.github.joke.percolate.processor.spi.ResolveCtx
import io.github.joke.percolate.processor.test.TypeUniverse

import javax.lang.model.type.TypeMirror
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Stream

final class DivergentBridge implements Bridge {

    private static final EdgeCodegen NO_OP_CODEGEN = { vars, inputs -> CodeBlock.of('') }
    private static final AtomicInteger COUNTER = new AtomicInteger(0)

    @Override
    Stream<BridgeStep> bridge(final TypeMirror from, final TypeMirror to, final ResolveCtx ctx) {
        final id = COUNTER.incrementAndGet()
        final elementSeed = new ElementSeed("divergent-${id}", TypeUniverse.STRING, TypeUniverse.STRING)
        Stream.of(new BridgeStep(TypeUniverse.STRING, TypeUniverse.STRING, 1, NO_OP_CODEGEN, [elementSeed]))
    }
}
