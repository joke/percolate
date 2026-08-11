package io.github.joke.percolate.processor;

import io.github.joke.percolate.processor.internal.graph.MapperGraph;
import io.github.joke.percolate.processor.internal.graph.Scope;
import io.github.joke.percolate.processor.model.GoalSpec;
import io.github.joke.percolate.processor.model.MapperShape;
import io.github.joke.percolate.processor.model.MethodDirectives;
import io.github.joke.percolate.spi.CallableMethods;
import io.github.joke.percolate.spi.ResolveCtx;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.TypeElement;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import static lombok.AccessLevel.NONE;

@RequiredArgsConstructor(onConstructor_ = @Inject)
@Getter
@Setter
public final class MapperContext {
    private final TypeElement mapperType;
    private @Nullable MapperShape shape;
    private @Nullable List<MethodDirectives> methodDirectives;
    private @Nullable MapperGraph graph;
    private @Nullable CallableMethods callableMethods;

    // The per-mapper ResolveCtx the expansion driver built, reused by generate for BodyCodegen rendering.
    private @Nullable ResolveCtx resolveCtx;

    // Per-method declared-bindings goal specs, keyed by the method's Scope (design D9).
    @SuppressWarnings("PMD.UseConcurrentHashMap") // single-threaded per-mapper context
    private final Map<Scope, GoalSpec> goalSpecs = new HashMap<>();

    // Diagnostics collected for this mapper's round, in report order (design D14) — flushed by MapperStep, never
    // eagerly.
    @Getter(NONE)
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    // Records diagnostic, collected rather than emitted.
    public void report(final Diagnostic diagnostic) {
        diagnostics.add(diagnostic);
    }

    // Every diagnostic recorded so far this round, in report order.
    public List<Diagnostic> getDiagnostics() {
        return List.copyOf(diagnostics);
    }

    // Whether any recorded diagnostic is an error (a warning never counts).
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.getSeverity() == Diagnostic.Severity.ERROR);
    }
}
