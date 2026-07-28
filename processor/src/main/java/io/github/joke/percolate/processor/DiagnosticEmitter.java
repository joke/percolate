package io.github.joke.percolate.processor;

import io.github.joke.percolate.spi.Subjects;
import jakarta.inject.Inject;
import java.util.List;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import lombok.RequiredArgsConstructor;

// The sole Messager writer (design D14 of change decouple-engine-from-strategy-semantics): resolves each
// Diagnostic's opaque io.github.joke.percolate.spi.Subject to its element/mirror/value, falling back to the
// mapper type for Subjects.none().
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class DiagnosticEmitter {

    private final Messager messager;

    // Emits every diagnostic in diagnostics, resolving Subjects.none() to mapperType.
    public void flush(final Element mapperType, final List<Diagnostic> diagnostics) {
        diagnostics.forEach(diagnostic -> emit(mapperType, diagnostic));
    }

    void emit(final Element mapperType, final Diagnostic diagnostic) {
        final var position = Subjects.resolve(diagnostic.getPosition(), mapperType);
        messager.printMessage(
                kind(diagnostic.getSeverity()),
                diagnostic.getMessage(),
                position.getElement(),
                position.getMirror(),
                position.getValue());
    }

    javax.tools.Diagnostic.Kind kind(final Diagnostic.Severity severity) {
        return severity == Diagnostic.Severity.ERROR
                ? javax.tools.Diagnostic.Kind.ERROR
                : javax.tools.Diagnostic.Kind.WARNING;
    }
}
