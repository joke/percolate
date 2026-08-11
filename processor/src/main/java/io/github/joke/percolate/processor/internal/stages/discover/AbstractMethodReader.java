package io.github.joke.percolate.processor.internal.stages.discover;

import com.groupcdg.pitest.annotations.CoverageIgnore;
import jakarta.inject.Inject;
import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import lombok.RequiredArgsConstructor;

import static com.google.auto.common.MoreElements.getLocalAndInheritedMethods;
import static java.util.stream.Collectors.toUnmodifiableList;

// The thin javax.lang.model leaf of abstract-method discovery: it enumerates a mapper type's local and
// inherited methods (getLocalAndInheritedMethods) and projects each into a plain
// AbstractMethodDescriptor, resolving the declared-on-Object flag against the java.lang.Object element here so
// the AbstractMethodFilter needs no javax comparison. Covered end-to-end by the compile-based feature-e2e
// layer, not by a unit-test javac substrate.
@CoverageIgnore
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class AbstractMethodReader {

    private final Elements elements;
    private final Types types;

    List<AbstractMethodDescriptor> readMethods(final TypeElement typeElement) {
        final var objectElement = elements.getTypeElement("java.lang.Object");
        return getLocalAndInheritedMethods(typeElement, types, elements).stream()
                .map(method -> describe(method, objectElement))
                .collect(toUnmodifiableList());
    }

    AbstractMethodDescriptor describe(final ExecutableElement method, final TypeElement objectElement) {
        return new AbstractMethodDescriptor(method.getModifiers(), enclosingIsObject(method, objectElement), method);
    }

    boolean enclosingIsObject(final ExecutableElement method, final TypeElement objectElement) {
        final var enclosing = method.getEnclosingElement();
        return enclosing != null && enclosing.equals(objectElement);
    }
}
