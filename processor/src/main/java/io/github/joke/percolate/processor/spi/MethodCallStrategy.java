package io.github.joke.percolate.processor.spi;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.processor.transform.TransformProposal;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;

@AutoService(TypeTransformStrategy.class)
public final class MethodCallStrategy implements TypeTransformStrategy {

    @Override
    public Optional<TransformProposal> canProduce(final TypeMirror sourceType, final TypeMirror targetType,
            final ResolutionContext ctx) {
        final var types = ctx.getTypes();

        return ctx.getMethods().stream()
                .filter(m -> !Objects.equals(m, ctx.getCurrentMethod()))
                .filter(m -> types.isAssignable(sourceType, m.getOriginal().getSourceType()))
                .filter(m -> types.isAssignable(m.getOriginal().getTargetType(), targetType))
                .findFirst()
                .map(m -> {
                    final var methodName = m.getOriginal().getMethod().getSimpleName();
                    return new TransformProposal(
                            sourceType,
                            targetType,
                            input -> CodeBlock.of("$L($L)", methodName, input),
                            this);
                });
    }
}
