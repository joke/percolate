package io.github.joke.percolate.processor.spi;

import javax.lang.model.type.TypeMirror;
import java.util.stream.Stream;

public interface CallableMethods {
    Stream<MethodCandidate> producing(TypeMirror outputType);
}
