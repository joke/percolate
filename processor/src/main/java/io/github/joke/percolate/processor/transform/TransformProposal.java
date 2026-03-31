package io.github.joke.percolate.processor.transform;

import io.github.joke.percolate.processor.spi.TypeTransformStrategy;
import java.util.function.Function;
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
    private final @Nullable ElementConstraint elementConstraint;
    private final @Nullable Function<CodeTemplate, CodeTemplate> templateComposer;

    public TransformProposal(
            final TypeMirror requiredInput,
            final TypeMirror producedOutput,
            final CodeTemplate codeTemplate,
            final TypeTransformStrategy strategy) {
        this(requiredInput, producedOutput, codeTemplate, strategy, null, null);
    }
}
