package io.github.joke.percolate.processor.stages.expand.properties.fakes

import io.github.joke.percolate.spi.GroupBuild
import io.github.joke.percolate.spi.GroupTarget
import io.github.joke.percolate.spi.ResolveCtx

import javax.lang.model.type.TypeMirror

final class NoOpGroupTarget implements GroupTarget {

    @Override
    Optional<GroupBuild> buildFor(final TypeMirror returnType, final List<String> targetTails, final ResolveCtx ctx) {
        Optional.empty()
    }
}
