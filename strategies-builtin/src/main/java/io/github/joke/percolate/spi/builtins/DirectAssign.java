package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.Bridge;
import io.github.joke.percolate.spi.BridgeStep;
import io.github.joke.percolate.spi.EdgeCodegen;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.Weights;
import lombok.NoArgsConstructor;

import javax.lang.model.type.TypeMirror;
import java.util.stream.Stream;

@AutoService(Bridge.class)
@NoArgsConstructor
public final class DirectAssign implements Bridge {

    @Override
    public Stream<BridgeStep> bridge(final TypeMirror from, final TypeMirror to, final ResolveCtx ctx) {
        if (ctx.types().isSameType(from, to)) {
            final EdgeCodegen codegen = (vars, inputs) -> CodeBlock.of("$L", inputs.single());
            return Stream.of(new BridgeStep(from, to, Weights.NOOP, codegen));
        }
        return Stream.empty();
    }
}
