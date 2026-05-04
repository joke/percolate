package io.github.joke.percolate.processor.spi;

import java.util.List;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;

public interface GroupTarget {
    Optional<GroupBuild> buildFor(TypeMirror returnType, List<String> targetTails, ResolveCtx ctx);

    default int priority() {
        return 0;
    }
}
