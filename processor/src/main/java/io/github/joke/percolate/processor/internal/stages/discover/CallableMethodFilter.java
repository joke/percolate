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
 * non-{@code Object} methods and hands them to an {@link IndexCallableMethods} view. Arity — including any
 * {@code @Ambient}-parameter adjustment — is entirely a strategy concern (design D7 of change
 * {@code decouple-engine-from-strategy-semantics}: the processor reads no user-facing annotation); {@code
 * MethodCallBridge} filters its own non-ambient parameter count on the candidates this index offers. It interrogates
 * no {@code javax.lang.model} value beyond {@link CandidateDescriptor}'s own plain fields, so it unit-tests on plain
 * descriptors.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class CallableMethodFilter {

    private final Types types;

    CallableMethods filter(final List<CandidateDescriptor> descriptors) {
        final var callable =
                descriptors.stream().filter(this::isCallable).distinct().collect(toUnmodifiableList());
        return new IndexCallableMethods(callable, types);
    }

    boolean isCallable(final CandidateDescriptor descriptor) {
        return descriptor.getKind() == METHOD && !descriptor.isEnclosingIsObject();
    }
}
