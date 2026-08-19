package io.github.joke.percolate.processor.internal.stages.generate;

import io.github.joke.percolate.lib.javapoet.FieldSpec;
import io.github.joke.percolate.lib.javapoet.MethodSpec;
import java.util.List;
import lombok.Value;

// BuildMethodBodies.build's result: every method body, plus every strategy-requested class member in the shape
// its request declared — a field or a method (change add-setter-assembly).
@Value
final class MethodBodies {
    List<MethodImpl> bodies;
    List<FieldSpec> members;
    List<MethodSpec> memberMethods;
}
