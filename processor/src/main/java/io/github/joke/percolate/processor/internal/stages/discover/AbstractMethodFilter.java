package io.github.joke.percolate.processor.internal.stages.discover;

import jakarta.inject.Inject;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;

import static java.util.stream.Collectors.toUnmodifiableList;
import static javax.lang.model.element.Modifier.ABSTRACT;

// The pure decision half of abstract-method discovery: from plain AbstractMethodDescriptors it keeps the
// abstract, non-Object methods — the ones a mapper must implement — returning their opaque ExecutableElement
// tokens. It interrogates no javax.lang.model value, so it unit-tests on plain descriptors with the tokens as
// never-stubbed opaque Mock()s.
@NoArgsConstructor(onConstructor_ = @Inject)
final class AbstractMethodFilter {

    @VisibleForTesting
    List<ExecutableElement> abstractMethods(final List<AbstractMethodDescriptor> descriptors) {
        return descriptors.stream()
                .filter(this::isAbstract)
                .filter(descriptor -> !descriptor.isEnclosingIsObject())
                .map(AbstractMethodDescriptor::getMethod)
                .collect(toUnmodifiableList());
    }

    @VisibleForTesting
    boolean isAbstract(final AbstractMethodDescriptor descriptor) {
        return descriptor.getModifiers().contains(ABSTRACT);
    }
}
