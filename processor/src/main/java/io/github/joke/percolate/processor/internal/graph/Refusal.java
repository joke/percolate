package io.github.joke.percolate.processor.internal.graph;

import io.github.joke.percolate.spi.Subject;
import lombok.Value;

// A strategy's or the engine's own reason a demand could not be served (design D2 of change decouple-engine-
// from-strategy-semantics): the feature-neutral shape recorded on a Value's inadmissible list — an opaque
// Subject position handle and a message. It names no strategy, feature, or annotation.
@Value
public class Refusal {
    Subject subject;
    String message;
}
