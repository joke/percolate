package io.github.joke.percolate.processor.graph;

import io.github.joke.percolate.processor.spi.TypeTransformStrategy;
import io.github.joke.percolate.processor.transform.CodeTemplate;
import io.github.joke.percolate.processor.transform.TransformProposal;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@Getter
@RequiredArgsConstructor
public class TransformEdge {
    private final TypeTransformStrategy strategy;
    private final TransformProposal proposal;

    @Nullable
    private CodeTemplate codeTemplate;

    public void resolveTemplate(final CodeTemplate codeTemplate) {
        this.codeTemplate = codeTemplate;
    }

    @Override
    public String toString() {
        return strategy.getClass().getSimpleName();
    }
}
