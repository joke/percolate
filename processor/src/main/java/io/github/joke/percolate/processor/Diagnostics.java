package io.github.joke.percolate.processor;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class Diagnostics {

    private final Messager messager;

    private final Set<Element> scarred = new HashSet<>();

    private final Map<Element, Element> scarredWithEnclosing = new ConcurrentHashMap<>();

    public void error(
            final Element element,
            final @Nullable AnnotationMirror mirror,
            final @Nullable AnnotationValue value,
            final String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element, mirror, value);
        scarred.add(element);
        final var enclosing = element.getEnclosingElement();
        if (enclosing != null) {
            scarredWithEnclosing.put(element, enclosing);
        }
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
        return scarred.contains(element)
                || scarredWithEnclosing.values().stream().anyMatch(element::equals);
    }

    void reset() {
        scarred.clear();
        scarredWithEnclosing.clear();
    }
}
