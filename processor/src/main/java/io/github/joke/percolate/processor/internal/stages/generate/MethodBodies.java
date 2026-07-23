package io.github.joke.percolate.processor.internal.stages.generate;

import io.github.joke.percolate.lib.javapoet.FieldSpec;
import java.util.List;
import lombok.Value;

/** {@link BuildMethodBodies#build}'s result: every method body, plus every strategy-requested class member. */
@Value
final class MethodBodies {
    List<MethodImpl> bodies;
    List<FieldSpec> members;
}
