package io.github.joke.percolate.spi;

import java.util.stream.Stream;

public interface SourceStep {
    Stream<Step> stepsFrom(javax.lang.model.type.TypeMirror produces, String pathTail, ResolveCtx ctx);

    default int priority() {
        return 0;
    }
}
