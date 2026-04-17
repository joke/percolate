package io.github.joke.percolate.processor.match;

import java.util.Set;
import lombok.Value;

/**
 * Captures context when a source-path segment could not be resolved during
 * {@code BuildValueGraphStage}, allowing {@code ValidateResolutionStage} to produce a rich
 * "Did you mean?" diagnostic.
 */
@Value
public class ResolutionFailure {
    String segmentName;
    Set<String> availableProperties;
}
