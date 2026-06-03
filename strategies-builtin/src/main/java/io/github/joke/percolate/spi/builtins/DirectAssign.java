package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.CombinatorialMatch;
import io.github.joke.percolate.spi.EdgeCodegen;
import io.github.joke.percolate.spi.ExpansionStep;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Slot;
import io.github.joke.percolate.spi.Weights;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

/**
 * Same-type identity assignment as a zero-cost {@code CONVERSION}: when a candidate's type equals the target, the
 * value is re-typed in place by folding an identity edge from the existing candidate (no new value is produced).
 * As a {@link io.github.joke.percolate.spi.Intent#CONVERSION} it folds into the current group's view rather than
 * opening a sub-group; a round-trip that reuses a downstream node closes a cycle the driver rejects.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class DirectAssign implements CombinatorialMatch {

    @Override
    public Stream<ExpansionStep> bridge(final TypeMirror from, final TypeMirror to, final ResolveCtx ctx) {
        if (!ctx.types().isSameType(from, to)) {
            return Stream.empty();
        }
        final EdgeCodegen codegen = (vars, inputs) -> CodeBlock.of("$L", inputs.single());
        final var input = new Slot("value", from, Weights.NOOP, null);
        return Stream.of(ExpansionStep.conversion(input, to, codegen, Weights.NOOP));
    }
}
