package io.github.joke.percolate.processor.stages.expand.properties.fakes

import io.github.joke.percolate.spi.ResolveCtx
import io.github.joke.percolate.spi.SourceStep
import io.github.joke.percolate.spi.Step

import javax.lang.model.type.TypeMirror
import java.util.stream.Stream

final class NoOpSourceStep implements SourceStep {

    @Override
    Stream<Step> stepsFrom(final TypeMirror produces, final String pathTail, final ResolveCtx ctx) {
        Stream.empty()
    }
}
