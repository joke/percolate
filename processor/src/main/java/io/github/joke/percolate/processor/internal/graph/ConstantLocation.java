package io.github.joke.percolate.processor.internal.graph;

import lombok.Value;

import static io.github.joke.percolate.processor.internal.graph.Location.Role.CONSTANT;

// The Location of a constant-value node: the untyped origin a @Map(constant = "...") directive plants in the
// graph, carrying the raw literal string. It is deliberately neither a SourceLocation nor a TargetLocation so
// the driver and code generator can tell a literal origin apart from a moved source value and from a target
// slot. The raw string is typed and rendered later by the ConstantValue strategy from the demanded target type;
// the seed stage never coerces it.
@Value
public class ConstantLocation implements Location {

    String raw;

    @Override
    public Role role() {
        return CONSTANT;
    }

    @Override
    public String segment() {
        return "const[" + raw + "]";
    }

    @Override
    public String slotName() {
        return "constant";
    }
}
