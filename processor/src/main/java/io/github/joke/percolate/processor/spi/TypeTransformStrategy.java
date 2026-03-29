package io.github.joke.percolate.processor.spi;

import io.github.joke.percolate.processor.transform.TransformProposal;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;

public interface TypeTransformStrategy {

    Optional<TransformProposal> canProduce(TypeMirror sourceType, TypeMirror targetType, ResolutionContext ctx);
}
