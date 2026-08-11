package io.github.joke.percolate.processor;

import jakarta.inject.Inject;
import java.util.List;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic.Kind;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;

import static io.github.joke.percolate.spi.Subjects.resolve;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.WARNING;

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

    @VisibleForTesting
    void emit(final Element mapperType, final Diagnostic diagnostic) {
        final var position = resolve(diagnostic.getPosition(), mapperType);
        messager.printMessage(
                kind(diagnostic.getSeverity()),
                diagnostic.getMessage(),
                position.getElement(),
                position.getMirror(),
                position.getValue());
    }

    // Percolate's Diagnostic.Severity and javac's Diagnostic.Kind both declare ERROR, so only one of the two
    // can be static-imported; the other stays qualified.
    @VisibleForTesting
    @SuppressWarnings("PMD.UseStaticImports")
    Kind kind(final Diagnostic.Severity severity) {
        return severity == Diagnostic.Severity.ERROR ? ERROR : WARNING;
    }
}
