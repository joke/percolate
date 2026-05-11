package io.github.joke.percolate.processor.spi;

import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;

public interface CallableMethods {
    Stream<MethodCandidate> producing(TypeMirror outputType);
}
