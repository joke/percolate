package io.github.joke.percolate.processor;

import io.github.joke.percolate.spi.Subjects;
import jakarta.inject.Inject;
import java.util.List;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import lombok.RequiredArgsConstructor;

/**
 * The sole {@link Messager} writer (design D14 of change {@code decouple-engine-from-strategy-semantics}): resolves
 * each {@link Diagnostic}'s opaque {@link io.github.joke.percolate.spi.Subject} to its element/mirror/value,
 * falling back to the mapper type for {@link Subjects#none()}.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class DiagnosticEmitter {

    private final Messager messager;

    /** Emits every diagnostic in {@code diagnostics}, resolving {@link Subjects#none()} to {@code mapperType}. */
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

    static javax.tools.Diagnostic.Kind kind(final Diagnostic.Severity severity) {
        return severity == Diagnostic.Severity.ERROR
                ? javax.tools.Diagnostic.Kind.ERROR
                : javax.tools.Diagnostic.Kind.WARNING;
    }
}
