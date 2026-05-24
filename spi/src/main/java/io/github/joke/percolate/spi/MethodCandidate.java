package io.github.joke.percolate.spi;

import lombok.Value;

import javax.lang.model.element.ExecutableElement;

@Value
public class MethodCandidate {
    ExecutableElement method;
    Receiver receiver;
}
