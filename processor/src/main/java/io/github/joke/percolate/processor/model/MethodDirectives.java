package io.github.joke.percolate.processor.model;

import io.github.joke.percolate.spi.Constraint;
import io.github.joke.percolate.spi.DirectiveInput;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.ExecutableElement;
import lombok.Value;

// One method's raw, un-validated io.github.joke.percolate.spi.DirectiveSink recordings (design D7 of change
// decouple-engine-from-strategy-semantics) — every reader's bind/input/scopeInput/ constrain call, merged,
// before the duplicate-target and source-root rules diagnose and the goal spec is derived (see
// GoalSpecFactory.from).
@Value
public class MethodDirectives {
    ExecutableElement method;
    List<Bind> binds;
    Map<String, List<DirectiveInput>> inputsByTarget;
    List<ScopeInputOverride> scopeInputOverrides;
    Map<String, List<Constraint>> constraintsByTarget;
}
