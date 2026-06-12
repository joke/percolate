package io.github.joke.percolate.processor.graph;

import lombok.Value;

/**
 * The {@link Location} of a constant-value node: the untyped origin a {@code @Map(constant = "...")} directive
 * plants in the graph, carrying the raw literal string. It is deliberately neither a {@link SourceLocation} nor a
 * {@link TargetLocation} so the driver and code generator can tell a literal origin apart from a moved source value
 * and from a target slot. The raw string is typed and rendered later by the {@code ConstantValue} strategy from the
 * demanded target type; the seed stage never coerces it.
 */
@Value
public class ConstantLocation implements Location {

    String raw;

    @Override
    public String segment() {
        return "const[" + raw + "]";
    }

    @Override
    public String slotName() {
        return "constant";
    }
}
