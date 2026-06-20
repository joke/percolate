package io.github.joke.percolate.processor.stages.expand;

import io.github.joke.percolate.processor.graph.MethodScope;
import io.github.joke.percolate.processor.graph.Scope;
import io.github.joke.percolate.processor.nullability.NullabilityResolver;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The single source-path accessor-resolution helper for the expansion driver (demand-driven-expansion D2). It is
 * used two ways and mutates no graph: a memoized, non-mutating forward {@link #typing} walk that types a
 * source-path demand at creation (base case: a method parameter's declared {@code (type, nullness)} read from the
 * signature; step: the accessor's output typing), and {@link #resolveAccessor}, which the work-list ACCESS handler
 * uses to emit the per-segment accessor Operation. The accessor's output type is strategy-determined, so both paths
 * go through the same accessor-strategy query — never a re-implemented match — and the graph still grows strictly
 * target-to-source (only this pure typing walk reads forward).
 */
@RequiredArgsConstructor
final class AccessorResolver {

    private static final int SINGLE_SEGMENT = 1;

    private final List<ExpansionStrategy> strategies;
    private final ResolveCtx resolveCtx;
    private final NullabilityResolver resolver;

    @SuppressWarnings("PMD.UseConcurrentHashMap") // single-threaded per-run source-path typing memo
    private final Map<String, Typing> typingMemo = new HashMap<>();

    /** The forward, memoized, non-mutating typing of a source path: type resolution, never graph mutation. */
    @Nullable
    Typing typing(final Scope scope, final List<String> segments) {
        final var key = scope.encode() + "::" + String.join(".", segments);
        if (typingMemo.containsKey(key)) {
            return typingMemo.get(key);
        }
        final var typing = computeTyping(scope, segments);
        typingMemo.put(key, typing);
        return typing;
    }

    private @Nullable Typing computeTyping(final Scope scope, final List<String> segments) {
        if (segments.isEmpty()) {
            return null;
        }
        if (segments.size() == SINGLE_SEGMENT) {
            return paramTyping(scope, segments.get(0));
        }
        final var parent = typing(scope, segments.subList(0, segments.size() - 1));
        if (parent == null) {
            return null;
        }
        final var spec = resolveAccessor(parent.getType(), segments.get(segments.size() - 1));
        return spec == null ? null : new Typing(spec.getOutputType(), spec.getOutputNullness());
    }

    private @Nullable Typing paramTyping(final Scope scope, final String name) {
        if (!(scope instanceof MethodScope)) {
            return null;
        }
        return ((MethodScope) scope)
                .getMethod().getParameters().stream()
                        .filter(param -> param.getSimpleName().toString().equals(name))
                        .findFirst()
                        .map(param -> new Typing(param.asType(), resolver.resolve(param.asType(), param)))
                        .orElse(null);
    }

    /** The single one-port accessor a strategy produces for {@code segment} on {@code parentType}, else null. */
    @Nullable
    OperationSpec resolveAccessor(final TypeMirror parentType, final String segment) {
        final var demand = new DemandView(
                parentType,
                Nullability.NON_NULL,
                Optional.of(BindingDirective.segment(segment)),
                Set.of(),
                segment,
                resolver);
        return strategies.stream()
                .flatMap(strategy -> strategy.expand(demand, resolveCtx))
                .filter(spec -> spec.getPorts().size() == 1
                        && spec.getChildScope().isEmpty()
                        && resolveCtx.types().isSameType(spec.getPorts().get(0).getType(), parentType)
                        && !resolveCtx.types().isSameType(spec.getOutputType(), parentType))
                .findFirst()
                .orElse(null);
    }
}
