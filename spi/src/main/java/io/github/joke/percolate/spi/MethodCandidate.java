package io.github.joke.percolate.spi;

import javax.lang.model.element.ExecutableElement;
import lombok.Value;

@Value
public final class MethodCandidate {
    ExecutableElement method;
    Receiver receiver;
}
