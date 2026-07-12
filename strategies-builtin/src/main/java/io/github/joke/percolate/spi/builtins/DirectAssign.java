package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.javapoet.CodeBlock;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.OperationCodegen;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ProduceDemand;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Weights;
import java.util.List;
import java.util.stream.Stream;
import lombok.NoArgsConstructor;

/**
 * Same-type identity assignment as a zero-cost unary {@link OperationSpec} (design D1), target-driven: for any demand
 * it offers an identity that produces the target from a same-typed, same-nullness source — the value flows through
 * unchanged. Its single port is <b>reuse-only</b> ({@link Port#reuse}): the driver binds an in-scope source of the
 * demanded type/nullness, or the operation does not apply — it never mints one. (You do not manufacture a value just
 * to copy it to itself; a same-type produced value already feeds the target directly.) It reads no candidate.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class DirectAssign implements ExpansionStrategy {

    @Override
    public Stream<OperationSpec> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        final OperationCodegen codegen = inputs -> CodeBlock.of("$L", inputs.single());
        final var port = Port.reuse("value", to, demand.targetNullness());
        return Stream.of(OperationSpec.of("assign", codegen, Weights.NOOP, List.of(port), to, demand.targetNullness()));
    }
}
