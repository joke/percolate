package io.github.joke.percolate.processor.model;

import io.github.joke.percolate.spi.Visibility;
import javax.lang.model.element.VariableElement;
import lombok.Value;

// One io.github.joke.percolate.spi.DirectiveSink.scopeInput call recorded by a reader (design D5/D7 of change
// decouple-engine-from-strategy-semantics): the published name and Visibility for a mapper method parameter,
// overriding the engine's own default (the parameter's simple name, Visibility.LOCAL).
@Value
public class ScopeInputOverride {
    VariableElement parameter;
    String name;
    Visibility visibility;
}
