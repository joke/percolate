package io.github.joke.percolate.processor.stages.expand;

import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.EdgeCodegen;
import io.github.joke.percolate.spi.IncomingValues;
import io.github.joke.percolate.spi.VarNames;

/**
 * Codegen for a directive-binding direct-assign edge: emits the single incoming value verbatim. Extracted to a
 * named type so the directive-binding edge carries a stable, identifiable codegen rather than an inline lambda.
 */
final class DirectAssignCodegen implements EdgeCodegen {

    static final DirectAssignCodegen INSTANCE = new DirectAssignCodegen();

    private DirectAssignCodegen() {}

    @Override
    public CodeBlock render(final VarNames vars, final IncomingValues inputs) {
        return CodeBlock.of("$L", inputs.single());
    }
}
