package io.github.joke.percolate.spi;

import lombok.Value;

import javax.lang.model.element.ExecutableElement;

@Value
public final class MethodCandidate {
    ExecutableElement method;
    Receiver receiver;
}
