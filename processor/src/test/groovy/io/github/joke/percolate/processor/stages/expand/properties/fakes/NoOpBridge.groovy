package io.github.joke.percolate.processor.stages.expand.properties.fakes

import io.github.joke.percolate.processor.spi.Bridge
import io.github.joke.percolate.processor.spi.BridgeStep
import io.github.joke.percolate.processor.spi.ResolveCtx

import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

final class NoOpBridge implements Bridge {

    @Override
    Stream<BridgeStep> bridge(final TypeMirror from, final TypeMirror to, final ResolveCtx ctx) {
        Stream.empty()
    }
}
