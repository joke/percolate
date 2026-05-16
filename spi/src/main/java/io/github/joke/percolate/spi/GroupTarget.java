package io.github.joke.percolate.spi;

import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Optional;

public interface GroupTarget {
    Optional<GroupBuild> buildFor(TypeMirror returnType, List<String> targetTails, ResolveCtx ctx);

    default int priority() {
        return 0;
    }
}
