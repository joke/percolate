package io.github.joke.percolate.processor.transform;

import java.util.Set;
import javax.lang.model.type.TypeMirror;
import lombok.Value;

/**
 * Captures context when a source chain segment could not be resolved,
 * allowing ValidateTransformsStage to produce rich diagnostics.
 */
@Value
public class AccessResolutionFailure {
    String segmentName;
    int segmentIndex;
    String fullChain;
    TypeMirror searchedType;
    Set<String> availableProperties;
}
