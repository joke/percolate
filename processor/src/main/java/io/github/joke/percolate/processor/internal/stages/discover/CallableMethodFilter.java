package io.github.joke.percolate.processor.internal.stages.discover;

import io.github.joke.percolate.spi.CallableMethods;
import jakarta.inject.Inject;
import java.util.List;
import javax.lang.model.util.Types;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;

import static java.util.stream.Collectors.toUnmodifiableList;
import static javax.lang.model.element.ElementKind.METHOD;

// The pure decision half of callable-method discovery: from plain CandidateDescriptors it keeps the non-Object
// methods and hands them to an IndexCallableMethods view. Arity — including any @Ambient-parameter adjustment —
// is entirely a strategy concern (design D7 of change decouple-engine-from-strategy-semantics: the processor
// reads no user-facing annotation); MethodCallBridge filters its own non-ambient parameter count on the
// candidates this index offers. It interrogates no javax.lang.model value beyond CandidateDescriptor's own
// plain fields, so it unit-tests on plain descriptors.
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class CallableMethodFilter {

    private final Types types;

    @VisibleForTesting
    CallableMethods filter(final List<CandidateDescriptor> descriptors) {
        final var callable =
                descriptors.stream().filter(this::isCallable).distinct().collect(toUnmodifiableList());
        return new IndexCallableMethods(callable, types);
    }

    @VisibleForTesting
    boolean isCallable(final CandidateDescriptor descriptor) {
        return descriptor.getKind() == METHOD && !descriptor.isEnclosingIsObject();
    }
}
