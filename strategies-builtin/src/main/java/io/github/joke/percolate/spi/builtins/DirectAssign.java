package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.CombinatorialMatch;
import io.github.joke.percolate.spi.Demand;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Weights;
import java.util.List;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

/**
 * Same-type identity assignment as a zero-cost unary {@link OperationSpec}: when a candidate's type equals the
 * demanded type, the value is produced by an identity operation over that candidate (one port, nullness-transparent
 * — both the port and the output carry the demanded nullness). A round-trip that reuses a downstream Value closes a
 * cycle the engine never chooses (Horn derivations are well-founded).
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class DirectAssign implements CombinatorialMatch {

    @Override
    public Stream<OperationSpec> bridge(final TypeMirror from, final Demand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        if (!ctx.types().isSameType(from, to)) {
            return Stream.empty();
        }
        final OperationCodegen codegen = inputs -> CodeBlock.of("$L", inputs.single());
        final var port = new Port("value", from, demand.targetNullness());
        return Stream.of(OperationSpec.of("assign", codegen, Weights.NOOP, List.of(port), to, demand.targetNullness()));
    }
}
