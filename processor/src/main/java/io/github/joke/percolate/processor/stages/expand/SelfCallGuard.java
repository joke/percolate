package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.MethodScope;
import io.github.joke.percolate.processor.graph.Scope;
import io.github.joke.percolate.processor.graph.Value;
import io.github.joke.percolate.spi.CallableMethods;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import lombok.RequiredArgsConstructor;

/**
 * Per-method-scope candidate visibility for the expansion driver: a method consuming its own parameter to produce its
 * own output is always a degenerate self-call (infinite recursion) — at the return root ({@code return this.m(param)}),
 * behind an {@code iterate}/{@code collect} round-trip at a same-location sibling, or wrapped at a field
 * ({@code List.of(this.m(param))}). All such forms live in the method's own scope, so this guard hands the driver a
 * {@link ResolveCtx} whose {@link CallableMethods} excludes the current {@code MethodScope}'s method whenever the
 * demand being expanded belongs to that scope. Legitimate recursion is preserved because a container's per-element
 * transform is a separate child scope (where the base context applies). It is a cohesive collaborator (mirroring
 * {@link SourceCandidates} / {@link AccessorResolver}) so the driver stays the work-list dispatch + landing site; it
 * filters the {@link CallableMethods} the driver already holds, with no SPI change. Method identity is by signature
 * (name + parameter types), robust across {@code Element} instances.
 */
@RequiredArgsConstructor
final class SelfCallGuard {

    private final ResolveCtx base;
    private final Elements elements;
    private final Types types;

    @SuppressWarnings("PMD.UseConcurrentHashMap") // single-threaded per-mapper expansion
    private final Map<Scope, ResolveCtx> byScope = new HashMap<>();

    /** The base context, or — within a method's own scope — a view excluding that method from its own candidates. */
    ResolveCtx resolveCtxFor(final Value value) {
        if (!(value.getScope() instanceof MethodScope)) {
            return base;
        }
        return byScope.computeIfAbsent(value.getScope(), scope -> excluding(((MethodScope) scope).getMethod()));
    }

    private ResolveCtx excluding(final ExecutableElement method) {
        final var callableMethods = base.callableMethods();
        final CallableMethods filtered = callableMethods == null
                ? null
                : outputType -> callableMethods
                        .producing(outputType)
                        .filter(candidate -> !sameMethod(candidate.getMethod(), method));
        return new CompileResolveCtx(elements, types, filtered);
    }

    private static boolean sameMethod(final ExecutableElement candidate, final ExecutableElement excluded) {
        return signature(candidate).equals(signature(excluded));
    }

    private static String signature(final ExecutableElement method) {
        final var params = method.getParameters().stream()
                .map(parameter -> parameter.asType().toString())
                .collect(Collectors.joining(","));
        return method.getSimpleName() + "(" + params + ")";
    }
}
