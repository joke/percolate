package io.github.joke.percolate.processor.internal.stages.expand;

import io.github.joke.percolate.spi.Offer;
import io.github.joke.percolate.spi.PortType;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.List;
import java.util.Map;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;

/**
 * Matches one {@link PortType} template against one concrete {@link TypeMirror} source, recording each variable it
 * binds (design D4 of change {@code decompose-engine-stages}). A {@link PortType.Concrete} leaf matches by
 * {@code isSameType}; a {@link PortType.Var} binds (or re-checks an existing binding), first consulting its
 * {@link PortType.Bound} if it carries one (design D6 of change {@code decouple-engine-from-strategy-semantics}) —
 * a refused grounding is recorded to {@code refusals} rather than instantiated; a {@link PortType.App} recurses
 * structurally over its erasure and argument shapes — the sole genuine self-recursion in this collaborator
 * ({@link #unify} &rarr; {@link #unifyApp} &rarr; {@link #unify}), isolated in its spec with a {@code Spy}.
 */
@RequiredArgsConstructor
final class Unifier {

    /** Defensive bound on nested-generic recursion; subterm bindings already bound depth structurally. */
    private static final int MAX_DEPTH = 32;

    private final ResolveCtx ctx;

    /** Whether {@code template} matches {@code source} at {@code depth}, recording any variable it binds. */
    boolean unify(
            final PortType template,
            final TypeMirror source,
            final Map<Integer, TypeMirror> bindings,
            final int depth,
            final List<Offer> refusals) {
        if (depth > MAX_DEPTH) {
            return false;
        }
        if (template instanceof PortType.Concrete) {
            return ctx.isSameType(((PortType.Concrete) template).getType(), source);
        }
        if (template instanceof PortType.Var) {
            return bindVariable((PortType.Var) template, source, bindings, refusals);
        }
        // PortType is a closed pseudo-sealed hierarchy (Concrete/Var/App): having excluded the first two, this is App.
        return unifyApp((PortType.App) template, source, bindings, depth, refusals);
    }

    /**
     * Binds {@code var}'s index to {@code source} (or confirms an existing binding is the same type); refuses a
     * non-groundable source, and refuses (recording why) a source {@code var}'s own {@link PortType.Bound} rejects.
     */
    boolean bindVariable(
            final PortType.Var var,
            final TypeMirror source,
            final Map<Integer, TypeMirror> bindings,
            final List<Offer> refusals) {
        if (!isGroundable(source) || refusedByBound(var, source, refusals)) {
            return false;
        }
        final var index = var.getIndex();
        final var existing = bindings.get(index);
        if (existing != null) {
            return ctx.isSameType(existing, source);
        }
        bindings.put(index, source);
        return true;
    }

    /** Whether {@code var}'s own {@link PortType.Bound} rejects {@code source}, recording why to {@code refusals}. */
    boolean refusedByBound(final PortType.Var var, final TypeMirror source, final List<Offer> refusals) {
        final var bound = var.getBound();
        if (bound == null) {
            return false;
        }
        final var refusal = bound.check(source, ctx);
        refusal.ifPresent(refusals::add);
        return refusal.isPresent();
    }

    /** Whether the parameterised {@code template} matches the declared {@code source}, unifying each argument in turn. */
    boolean unifyApp(
            final PortType.App template,
            final TypeMirror source,
            final Map<Integer, TypeMirror> bindings,
            final int depth,
            final List<Offer> refusals) {
        if (!matchesErasure(template, source, ctx)) {
            return false;
        }
        final var templateArgs = template.getArgs();
        for (int i = 0; i < templateArgs.size(); i++) {
            if (!unify(templateArgs.get(i), ctx.typeArgument(source, i), bindings, depth + 1, refusals)) {
                return false;
            }
        }
        return true;
    }

    // static (not an instance method): unifyApp is exercised through a Spy in UnifierSpec to isolate the
    // self-recursive unify() call, and a static call bypasses the spy's interaction recording entirely — unlike
    // an instance helper, which would show up as an extra untracked interaction under strict `0 * _` mocking.
    /** Whether {@code source} is declared, erases to {@code template}'s erasure, and has as many type arguments. */
    static boolean matchesErasure(final PortType.App template, final TypeMirror source, final ResolveCtx ctx) {
        return ctx.isDeclared(source)
                && ctx.isSameType(
                        ctx.erasure(source), ctx.erasure(template.getErasure().asType()))
                && ctx.typeArgumentCount(source) == template.getArgs().size();
    }

    /** Restrict-v1 policy: a variable matches only an invariant reference argument; never a wildcard/type-variable. */
    boolean isGroundable(final TypeMirror source) {
        return ctx.isDeclared(source) || ctx.isArray(source);
    }
}
