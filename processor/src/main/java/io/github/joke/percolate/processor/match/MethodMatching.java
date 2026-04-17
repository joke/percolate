package io.github.joke.percolate.processor.match;

import io.github.joke.percolate.processor.model.MappingMethodModel;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import lombok.Value;

/**
 * Groups the ordered list of {@link MappingAssignment}s for one mapper method.
 *
 * <p>Assignment order: explicit {@code @Map} directives first (in source-declaration order),
 * followed by auto-mapped entries (in target-property declaration order).
 */
@Value
public class MethodMatching {
    ExecutableElement method;
    MappingMethodModel model;
    List<MappingAssignment> assignments;
}
