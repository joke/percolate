package io.github.joke.percolate.processor.internal.stages.discover;

import static java.util.stream.Collectors.toUnmodifiableList;
import static javax.lang.model.element.ElementKind.METHOD;

import io.github.joke.percolate.spi.CallableMethods;
import jakarta.inject.Inject;
import java.util.List;
import javax.lang.model.util.Types;
import lombok.RequiredArgsConstructor;

/**
 * The pure decision half of callable-method discovery: from plain {@link CandidateDescriptor}s it keeps the
 * single-parameter, non-{@code Object} methods and hands them to an {@link IndexCallableMethods} view. It interrogates
 * no {@code javax.lang.model} value (the return-type/output {@link javax.lang.model.type.TypeMirror}s stay opaque
 * tokens; assignability is the {@code IndexCallableMethods}' one seam question), so it unit-tests on plain descriptors.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class CallableMethodFilter {

    private static final int SINGLE_PARAM_COUNT = 1;

    private final Types types;

    CallableMethods filter(final List<CandidateDescriptor> descriptors) {
        final var callable =
                descriptors.stream().filter(this::isCallable).distinct().collect(toUnmodifiableList());
        return new IndexCallableMethods(callable, types);
    }

    boolean isCallable(final CandidateDescriptor descriptor) {
        return descriptor.getKind() == METHOD
                && !descriptor.isEnclosingIsObject()
                && descriptor.getParameterCount() == SINGLE_PARAM_COUNT;
    }
}
