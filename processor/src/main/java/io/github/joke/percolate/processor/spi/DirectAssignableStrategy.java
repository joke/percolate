package io.github.joke.percolate.processor.spi;

import com.google.auto.service.AutoService;
import io.github.joke.percolate.processor.transform.TransformProposal;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;

@AutoService(TypeTransformStrategy.class)
public final class DirectAssignableStrategy implements TypeTransformStrategy {

    @Override
    public Optional<TransformProposal> canProduce(
            final TypeMirror sourceType, final TypeMirror targetType, final ResolutionContext ctx) {
        if (ctx.getTypes().isAssignable(sourceType, targetType)) {
            return Optional.of(new TransformProposal(sourceType, targetType, input -> input, this));
        }
        return Optional.empty();
    }
}
