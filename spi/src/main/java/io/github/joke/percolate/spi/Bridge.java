package io.github.joke.percolate.spi;

import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;

public interface Bridge {
    Stream<BridgeStep> bridge(TypeMirror from, TypeMirror to, ResolveCtx ctx);

    default int priority() {
        return 0;
    }
}
