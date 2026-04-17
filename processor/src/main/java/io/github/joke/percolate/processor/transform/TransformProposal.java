package io.github.joke.percolate.processor.transform;

import io.github.joke.percolate.processor.graph.LiftKind;
import io.github.joke.percolate.processor.spi.TypeTransformStrategy;
import javax.lang.model.type.TypeMirror;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@Getter
@RequiredArgsConstructor
public final class TransformProposal {
    private final TypeMirror requiredInput;
    private final TypeMirror producedOutput;
    private final CodeTemplate codeTemplate;
    private final TypeTransformStrategy strategy;
    private final @Nullable LiftKind liftKind;
    private final @Nullable TypeMirror liftInnerInput;
    private final @Nullable TypeMirror liftInnerOutput;

    public TransformProposal(
            final TypeMirror requiredInput,
            final TypeMirror producedOutput,
            final CodeTemplate codeTemplate,
            final TypeTransformStrategy strategy) {
        this(requiredInput, producedOutput, codeTemplate, strategy, null, null, null);
    }
}
