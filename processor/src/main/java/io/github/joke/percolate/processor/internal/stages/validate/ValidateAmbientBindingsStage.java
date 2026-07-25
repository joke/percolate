package io.github.joke.percolate.processor.internal.stages.validate;

import io.github.joke.percolate.processor.Diagnostics;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.internal.graph.AmbientDecl;
import io.github.joke.percolate.processor.internal.graph.AmbientKeys;
import io.github.joke.percolate.processor.internal.graph.Location;
import io.github.joke.percolate.processor.internal.graph.MapperGraph;
import io.github.joke.percolate.processor.internal.graph.Scope;
import io.github.joke.percolate.processor.internal.graph.Value;
import io.github.joke.percolate.processor.internal.stages.Stage;
import io.github.joke.percolate.processor.model.MapperShape;
import io.github.joke.percolate.processor.nullability.NullabilityResolver;
import io.github.joke.percolate.spi.CallableMethods;
import io.github.joke.percolate.spi.ResolveCtx;
import jakarta.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The three ambient diagnostics (design {@code ambient-parameters}, Decisions 2/3), reported after expansion by
 * <b>independently re-deriving</b> what {@code MethodCallBridge}/{@code PortSourceResolver} attempted during
 * expansion — never by inspecting what landed, since an unresolved {@code AMBIENT} port declines exactly like
 * {@code REUSE} and leaves no {@code Operation} in the graph to inspect (see design.md's implementation note on
 * why the engine stays diagnostics-free). For every {@code FREE}-role {@link Value} actually demanded during
 * expansion, this walks the exact same {@code callableMethods.producing(type)} candidates {@code MethodCallBridge}
 * itself considers, and for each candidate's {@code @Ambient} parameters, resolves the key against
 * {@code value.getScope().ambientDecls()} — the same rule {@code PortSourceResolver} used, computed independently.
 * This is demand-scoped, not graph-scoped, so a candidate is only checked against the ambient environment of a
 * scope that could actually reach it (never a false positive from an unrelated abstract method sharing the
 * mapper type). Duplicate ambient keys are checked per abstract method and per encountered candidate method.
 *
 * <p>All three diagnostics are positioned at the mapper type itself, not the offending {@code @Ambient}
 * parameter: {@link Diagnostics#hasErrorsFor} — which {@link RealisationDiagnosticsStage} checks to suppress its
 * own generic "no plan" message — is only satisfied by scarring the mapper type or an element whose
 * <em>immediate</em> enclosing element is the mapper type. The offending parameter's enclosing element is its
 * method, not the mapper type, and an inherited candidate's own enclosing element is its declaring supertype —
 * so this is the only positioning that suppresses correctly across both directly-declared and inherited
 * candidates. Every message still names the key, method, parameter, and (for a mismatch) both types.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class ValidateAmbientBindingsStage implements Stage {

    private final Diagnostics diagnostics;
    private final NullabilityResolver resolver;

    @Override
    public void run(final MapperContext ctx) {
        final var shape = ctx.getShape();
        if (shape == null || diagnostics.hasErrorsFor(ctx.getMapperType())) {
            return;
        }
        // Discovery always sets callableMethods unconditionally, and expand always sets graph/resolveCtx
        // together whenever shape is non-null (pipeline order: discover, then expand, then here) — so once
        // shape is non-null, the other three are guaranteed set too.
        final var graph = Objects.requireNonNull(ctx.getGraph(), "graph set once shape is non-null");
        final var callableMethods =
                Objects.requireNonNull(ctx.getCallableMethods(), "callableMethods set unconditionally by discovery");
        final var resolveCtx = Objects.requireNonNull(ctx.getResolveCtx(), "resolveCtx set once shape is non-null");
        checkAll(graph, shape, callableMethods, resolveCtx, ctx);
    }

    private void checkAll(
            final MapperGraph graph,
            final MapperShape shape,
            final CallableMethods callableMethods,
            final ResolveCtx resolveCtx,
            final MapperContext ctx) {
        final Set<ExecutableElement> duplicateChecked = new HashSet<>();
        final Set<List<Object>> bindingChecked = new HashSet<>();
        shape.getAbstractMethods().forEach(method -> checkDuplicateKeys(method, ctx, duplicateChecked));
        graph.values()
                .filter(value -> value.getLoc().role() == Location.Role.FREE)
                .forEach(value ->
                        checkCandidates(value, callableMethods, resolveCtx, ctx, duplicateChecked, bindingChecked));
    }

    private void checkCandidates(
            final Value value,
            final CallableMethods callableMethods,
            final ResolveCtx resolveCtx,
            final MapperContext ctx,
            final Set<ExecutableElement> duplicateChecked,
            final Set<List<Object>> bindingChecked) {
        callableMethods.producing(value.type()).forEach(candidate -> {
            final var method = candidate.getMethod();
            checkDuplicateKeys(method, ctx, duplicateChecked);
            method.getParameters()
                    .forEach(param -> checkBinding(param, method, value.getScope(), resolveCtx, ctx, bindingChecked));
        });
    }

    private void checkDuplicateKeys(
            final ExecutableElement method, final MapperContext ctx, final Set<ExecutableElement> checked) {
        if (!checked.add(method)) {
            return;
        }
        final Set<String> seen = new HashSet<>();
        method.getParameters().stream()
                .map(AmbientKeys::keyOf)
                .filter(Objects::nonNull)
                .filter(key -> !seen.add(key))
                .forEach(key -> reportDuplicateKey(key, method, ctx));
    }

    private void reportDuplicateKey(final String key, final ExecutableElement method, final MapperContext ctx) {
        diagnostics.error(
                ctx.getMapperType(),
                "duplicate @Ambient key '" + key + "' on " + method.getSimpleName()
                        + ": another @Ambient parameter of this method already publishes this key");
    }

    private void checkBinding(
            final VariableElement param,
            final ExecutableElement method,
            final Scope scope,
            final ResolveCtx resolveCtx,
            final MapperContext ctx,
            final Set<List<Object>> checked) {
        final var key = AmbientKeys.keyOf(param);
        if (key == null) {
            return;
        }
        if (!checked.add(List.of(scope, method, param))) {
            return;
        }
        reportIfUnresolved(key, param, method, scope, resolveCtx, ctx);
    }

    private void reportIfUnresolved(
            final String key,
            final VariableElement param,
            final ExecutableElement method,
            final Scope scope,
            final ResolveCtx resolveCtx,
            final MapperContext ctx) {
        final var binding = findBinding(scope, key);
        if (binding == null) {
            reportUnboundKey(key, param, method, ctx);
            return;
        }
        if (!resolveCtx.isAssignable(binding.getType(), param.asType())) {
            reportTypeMismatch(key, binding, param, method, ctx);
        }
    }

    @Nullable
    private AmbientDecl findBinding(final Scope scope, final String key) {
        return scope.ambientDecls(resolver::resolve)
                .filter(decl -> decl.getKey().equals(key))
                .findFirst()
                .orElse(null);
    }

    private void reportUnboundKey(
            final String key, final VariableElement param, final ExecutableElement method, final MapperContext ctx) {
        diagnostics.error(
                ctx.getMapperType(),
                "unbound @Ambient key '" + key + "' for " + method.getSimpleName() + "'s parameter '"
                        + param.getSimpleName() + "': no enclosing mapper method publishes this key");
    }

    private void reportTypeMismatch(
            final String key,
            final AmbientDecl binding,
            final VariableElement param,
            final ExecutableElement method,
            final MapperContext ctx) {
        diagnostics.error(
                ctx.getMapperType(),
                "@Ambient key '" + key + "' is bound to " + binding.getType() + " but " + method.getSimpleName()
                        + "'s parameter '" + param.getSimpleName() + "' declares " + param.asType()
                        + ", which is not assignable");
    }
}
