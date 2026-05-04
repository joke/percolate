package io.github.joke.percolate.processor;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class Diagnostics {

    private final Messager messager;

    @EqualsAndHashCode.Include
    private final Set<Element> scarred = new HashSet<>();

    @EqualsAndHashCode.Include
    private final Map<Element, Element> scarredWithEnclosing = new HashMap<>();

    public void error(
            final Element element,
            final @Nullable AnnotationMirror mirror,
            final @Nullable AnnotationValue value,
            final String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element, mirror, value);
        scarred.add(element);
        scarredWithEnclosing.put(element, element.getEnclosingElement());
    }

    public void error(final Element element, final String message) {
        error(element, null, null, message);
    }

    public void warning(final Element element, final String message) {
        messager.printMessage(Diagnostic.Kind.WARNING, message, element);
    }

    public void warning(
            final Element element,
            final @Nullable AnnotationMirror mirror,
            final @Nullable AnnotationValue value,
            final String message) {
        messager.printMessage(Diagnostic.Kind.WARNING, message, element, mirror, value);
    }

    public boolean hasErrorsFor(final Element element) {
        if (scarred.contains(element)) {
            return true;
        }
        return scarredWithEnclosing.values().stream().anyMatch(element::equals);
    }

    void reset() {
        scarred.clear();
        scarredWithEnclosing.clear();
    }
}
