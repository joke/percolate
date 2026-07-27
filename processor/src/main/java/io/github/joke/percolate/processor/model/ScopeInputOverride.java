package io.github.joke.percolate.processor.model;

import io.github.joke.percolate.spi.Visibility;
import javax.lang.model.element.VariableElement;
import lombok.Value;

/**
 * One {@link io.github.joke.percolate.spi.DirectiveSink#scopeInput} call recorded by a reader (design D5/D7 of
 * change {@code decouple-engine-from-strategy-semantics}): the published name and {@link Visibility} for a mapper
 * method parameter, overriding the engine's own default (the parameter's simple name, {@link Visibility#LOCAL}).
 */
@Value
public class ScopeInputOverride {
    VariableElement parameter;
    String name;
    Visibility visibility;
}
