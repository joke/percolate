package io.github.joke.percolate.processor.internal.stages.discover;

import static java.util.stream.Collectors.toUnmodifiableList;

import com.groupcdg.pitest.annotations.CoverageIgnore;
import jakarta.inject.Inject;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import lombok.RequiredArgsConstructor;

/**
 * The thin {@code javax.lang.model} leaf of callable-method discovery: it enumerates a mapper type's members
 * ({@link Elements#getAllMembers}), keeps the executable ones, and projects each into a plain
 * {@link CandidateDescriptor}, resolving the {@code declared-on-Object} flag against the enclosing type's qualified
 * name here so the {@link CallableMethodFilter} needs no {@code javax} comparison. Covered end-to-end by the
 * compile-based feature-e2e layer, not by a unit-test javac substrate.
 */
@CoverageIgnore
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class CallableMethodIndexer {

    private static final String OBJECT_FQN = "java.lang.Object";

    private final Elements elements;

    List<CandidateDescriptor> index(final TypeElement mapperType) {
        return elements.getAllMembers(mapperType).stream()
                .filter(ExecutableElement.class::isInstance)
                .map(ExecutableElement.class::cast)
                .map(this::describe)
                .collect(toUnmodifiableList());
    }

    CandidateDescriptor describe(final ExecutableElement method) {
        return new CandidateDescriptor(
                method.getKind(),
                method.getParameters().size(),
                enclosingIsObject(method),
                method.getReturnType(),
                method);
    }

    boolean enclosingIsObject(final ExecutableElement method) {
        final Element enclosing = method.getEnclosingElement();
        return enclosing instanceof TypeElement
                && OBJECT_FQN.equals(
                        ((TypeElement) enclosing).getQualifiedName().toString());
    }
}
