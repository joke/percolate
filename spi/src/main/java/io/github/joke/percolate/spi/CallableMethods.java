package io.github.joke.percolate.spi;

import javax.lang.model.type.TypeMirror;
import java.util.stream.Stream;

public interface CallableMethods {
    Stream<MethodCandidate> producing(TypeMirror outputType);
}
