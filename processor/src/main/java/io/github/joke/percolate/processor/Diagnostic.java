package io.github.joke.percolate.processor;

import io.github.joke.percolate.spi.Subject;
import lombok.Value;

// A diagnostic as a value, attributed to a mapper's MapperContext rather than emitted eagerly (design D14 of
// change decouple-engine-from-strategy-semantics): position is an opaque Subject, resolved only by
// DiagnosticEmitter; permanent is false (transient) by default, an explicit opt-out for a diagnostic whose
// cause cannot change across a later processing round (Lombok interop). MapperStep defers a mapper iff it is
// unrealised and every recorded diagnostic is transient.
@Value
public class Diagnostic {

    Severity severity;
    Subject position;
    String message;
    boolean permanent;

    public enum Severity {
        ERROR,
        WARNING
    }

    // A transient error: wrong only if nothing changes by a later round.
    public static Diagnostic error(final Subject position, final String message) {
        return new Diagnostic(Severity.ERROR, position, message, false);
    }

    // A transient warning: never affects deferral or MapperContext.hasErrors().
    public static Diagnostic warning(final Subject position, final String message) {
        return new Diagnostic(Severity.WARNING, position, message, false);
    }

    // This diagnostic, marked permanent — an explicit opt-out of deferral.
    public Diagnostic asPermanent() {
        return new Diagnostic(severity, position, message, true);
    }
}
