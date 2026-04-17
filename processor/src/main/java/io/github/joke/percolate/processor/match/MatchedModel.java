package io.github.joke.percolate.processor.match;

import java.util.List;
import javax.lang.model.element.TypeElement;
import lombok.Value;

/**
 * The output of the matching layer (stages 2–3).
 *
 * <p>Carries the mapper {@link TypeElement} and one {@link MethodMatching} per mapper method.
 * Does not expose any graph or JGraphT type.
 */
@Value
public class MatchedModel {
    TypeElement mapperType;
    List<MethodMatching> methods;
}
