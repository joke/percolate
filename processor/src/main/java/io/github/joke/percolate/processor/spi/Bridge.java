package io.github.joke.percolate.processor.spi;

import java.util.Optional;
import javax.lang.model.type.TypeMirror;

public interface Bridge {
    Optional<BridgeStep> bridge(TypeMirror from, TypeMirror to, ResolveCtx ctx);

    default int priority() {
        return 0;
    }
}
