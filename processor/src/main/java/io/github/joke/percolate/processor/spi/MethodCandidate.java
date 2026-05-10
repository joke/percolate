package io.github.joke.percolate.processor.spi;

import lombok.Value;

import javax.lang.model.element.ExecutableElement;

@Value
public final class MethodCandidate {
    ExecutableElement method;
    Receiver receiver;
}
