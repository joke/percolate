package io.github.joke.percolate.processor.internal.stages.discover;

import static java.util.stream.Collectors.toUnmodifiableList;
import static javax.lang.model.element.Modifier.ABSTRACT;

import jakarta.inject.Inject;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import lombok.NoArgsConstructor;

/**
 * The pure decision half of abstract-method discovery: from plain {@link AbstractMethodDescriptor}s it keeps the
 * abstract, non-{@code Object} methods — the ones a mapper must implement — returning their opaque
 * {@link ExecutableElement} tokens. It interrogates no {@code javax.lang.model} value, so it unit-tests on plain
 * descriptors with the tokens as never-stubbed opaque {@code Mock()}s.
 */
@NoArgsConstructor(onConstructor_ = @Inject)
final class AbstractMethodFilter {

    List<ExecutableElement> abstractMethods(final List<AbstractMethodDescriptor> descriptors) {
        return descriptors.stream()
                .filter(this::isAbstract)
                .filter(descriptor -> !descriptor.isEnclosingIsObject())
                .map(AbstractMethodDescriptor::getMethod)
                .collect(toUnmodifiableList());
    }

    boolean isAbstract(final AbstractMethodDescriptor descriptor) {
        return descriptor.getModifiers().contains(ABSTRACT);
    }
}
