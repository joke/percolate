package io.github.joke.percolate.processor.spi;

import javax.lang.model.type.TypeMirror;
import java.util.stream.Stream;

public interface Bridge {
    Stream<BridgeStep> bridge(TypeMirror from, TypeMirror to, ResolveCtx ctx);

    default int priority() {
        return 0;
    }
}
