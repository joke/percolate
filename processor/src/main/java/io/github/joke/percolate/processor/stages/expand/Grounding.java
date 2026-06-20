package io.github.joke.percolate.processor.stages.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.spi.ChildScopeSpec;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.PortType;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.SourceProjection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Grounding-by-match (design D2/D5, change {@code target-driven-engine} §§ 2.2–2.4): the engine's generic,
 * SPI-agnostic mechanic for sourcing a type-variable {@link Port}. An {@link OperationSpec} with no type-variable
 * port passes through unchanged (the concrete path is untouched); a spec carrying a {@link PortType} template is
 * grounded by <b>unifying</b> each template port against the in-scope concrete source types, binding the variable to
 * each match, and <b>instantiating</b> one fully-concrete spec per consistent binding — substituting the bindings
 * across the spec's ports and child scope. When several sources match, every match is instantiated (over-emit); the
 * engine applies no preference (D1) and lets cost extraction prune the unreachable ones.
 *
 * <p>The unification is purely structural: it knows {@code isSameType}/{@code erasure}/type-argument shape and names
 * no container or conversion kind. It never produces an abstract type — a variable is grounded to a concrete source
 * subterm before any {@code Value} is demanded, so the work-list stays concrete (graph-expansion invariant). Per the
 * spike's wildcard policy (§ 1.4) a variable matches only an invariant reference argument (a {@code DECLARED} or
 * {@code ARRAY} type); a wildcard/type-variable argument does not unify, so no producer is invented (D4).
 */
@RequiredArgsConstructor
final class Grounding {

    /** Defensive bound on nested-generic recursion (D5 / § 1.4); subterm bindings already bound depth structurally. */
    private static final int MAX_DEPTH = 32;

    private final ResolveCtx ctx;
    private final List<SourceProjection> projections;

    /**
     * Grounds {@code spec} against the {@code sources} in scope: a spec with no type-variable port is returned as-is;
     * otherwise one concrete spec is emitted per consistent match (none when nothing unifies — no bridge invented).
     * The match set is the in-scope sources <b>widened</b> with the registered {@link SourceProjection}s' one-step
     * views of them (design D8) — so a {@code Stream<A>} port grounds against the {@code Stream<X>} a {@code List<X>}
     * source projects to. The engine consumes the projections structurally and names no kind.
     */
    Stream<OperationSpec> ground(final OperationSpec spec, final List<TypeMirror> sources) {
        final var templatePorts = spec.getPorts().stream()
                .filter(port -> port.getTemplate() != null)
                .collect(toUnmodifiableList());
        if (templatePorts.isEmpty()) {
            return Stream.of(spec);
        }
        final var matchSet = widen(sources);
        final var bindingSets = new ArrayList<Map<Integer, TypeMirror>>();
        assign(templatePorts, 0, matchSet, new HashMap<>(), bindingSets);
        return bindingSets.stream().map(bindings -> instantiate(spec, bindings));
    }

    /** The in-scope sources plus each projector's one-step view of them — the grounding match set (D8). */
    private List<TypeMirror> widen(final List<TypeMirror> sources) {
        if (projections.isEmpty()) {
            return sources;
        }
        final var widened = new ArrayList<>(sources);
        for (final var projection : projections) {
            for (final var source : sources) {
                projection.project(source, ctx).forEach(widened::add);
            }
        }
        return widened;
    }

    /** Assigns each template port to a unifying source, collecting every consistent binding map (the cross-product). */
    private void assign(
            final List<Port> ports,
            final int index,
            final List<TypeMirror> sources,
            final Map<Integer, TypeMirror> current,
            final List<Map<Integer, TypeMirror>> out) {
        if (index == ports.size()) {
            out.add(new HashMap<>(current));
            return;
        }
        final var template = Objects.requireNonNull(ports.get(index).getTemplate());
        for (final var source : sources) {
            final var trial = new HashMap<>(current);
            if (unify(template, source, trial, 0)) {
                assign(ports, index + 1, sources, trial, out);
            }
        }
    }

    // ---- unification (match a template against a concrete source, binding variables) ----------------------

    private boolean unify(
            final PortType template,
            final TypeMirror source,
            final Map<Integer, TypeMirror> bindings,
            final int depth) {
        if (depth > MAX_DEPTH) {
            return false;
        }
        if (template instanceof PortType.Concrete) {
            return ctx.types().isSameType(((PortType.Concrete) template).getType(), source);
        }
        if (template instanceof PortType.Var) {
            return bindVariable(((PortType.Var) template).getIndex(), source, bindings);
        }
        return template instanceof PortType.App && unifyApp((PortType.App) template, source, bindings, depth);
    }

    private boolean bindVariable(final int index, final TypeMirror source, final Map<Integer, TypeMirror> bindings) {
        if (!isGroundable(source)) {
            return false;
        }
        final var existing = bindings.get(index);
        if (existing != null) {
            return ctx.types().isSameType(existing, source);
        }
        bindings.put(index, source);
        return true;
    }

    private boolean unifyApp(
            final PortType.App template,
            final TypeMirror source,
            final Map<Integer, TypeMirror> bindings,
            final int depth) {
        if (source.getKind() != TypeKind.DECLARED) {
            return false;
        }
        final var types = ctx.types();
        if (!types.isSameType(
                types.erasure(source), types.erasure(template.getErasure().asType()))) {
            return false;
        }
        final var args = ((DeclaredType) source).getTypeArguments();
        final var templateArgs = template.getArgs();
        if (args.size() != templateArgs.size()) {
            return false;
        }
        for (int i = 0; i < templateArgs.size(); i++) {
            if (!unify(templateArgs.get(i), args.get(i), bindings, depth + 1)) {
                return false;
            }
        }
        return true;
    }

    /** Restrict-v1 policy: a variable matches only an invariant reference argument; never a wildcard/type-variable. */
    private static boolean isGroundable(final TypeMirror source) {
        final var kind = source.getKind();
        return kind == TypeKind.DECLARED || kind == TypeKind.ARRAY;
    }

    // ---- instantiation (substitute the bindings to build a fully-concrete spec) ---------------------------

    private OperationSpec instantiate(final OperationSpec spec, final Map<Integer, TypeMirror> bindings) {
        final var ports =
                spec.getPorts().stream().map(port -> groundPort(port, bindings)).collect(toUnmodifiableList());
        final var childScope = spec.getChildScope().map(child -> groundChild(child, bindings));
        if (childScope.isPresent()) {
            return OperationSpec.mapping(
                    spec.getLabel(),
                    spec.getCodegen(),
                    spec.getWeight(),
                    ports,
                    spec.getOutputType(),
                    spec.getOutputNullness(),
                    childScope.get());
        }
        if (spec.isPartial()) {
            return OperationSpec.ofPartial(
                    spec.getLabel(),
                    spec.getCodegen(),
                    spec.getWeight(),
                    ports,
                    spec.getOutputType(),
                    spec.getOutputNullness());
        }
        return OperationSpec.of(
                spec.getLabel(),
                spec.getCodegen(),
                spec.getWeight(),
                ports,
                spec.getOutputType(),
                spec.getOutputNullness());
    }

    private Port groundPort(final Port port, final Map<Integer, TypeMirror> bindings) {
        final var template = port.getTemplate();
        if (template == null) {
            return port;
        }
        return new Port(port.getName(), ground(template, bindings), port.getNullness());
    }

    private ChildScopeSpec groundChild(final ChildScopeSpec child, final Map<Integer, TypeMirror> bindings) {
        final var elementIn = groundOr(child.getElementInTemplate(), child.getElementIn(), bindings);
        final var elementOut = groundOr(child.getElementOutTemplate(), child.getElementOut(), bindings);
        return new ChildScopeSpec(elementIn, child.getElementInNullness(), elementOut, child.getElementOutNullness());
    }

    private TypeMirror groundOr(
            final @Nullable PortType template, final TypeMirror concrete, final Map<Integer, TypeMirror> bindings) {
        return template == null ? concrete : ground(template, bindings);
    }

    private TypeMirror ground(final PortType template, final Map<Integer, TypeMirror> bindings) {
        if (template instanceof PortType.Concrete) {
            return ((PortType.Concrete) template).getType();
        }
        if (template instanceof PortType.Var) {
            final var bound = bindings.get(((PortType.Var) template).getIndex());
            if (bound == null) {
                throw new IllegalStateException("Ungrounded type variable while instantiating: " + template);
            }
            return bound;
        }
        if (template instanceof PortType.App) {
            final var app = (PortType.App) template;
            final var args =
                    app.getArgs().stream().map(arg -> ground(arg, bindings)).toArray(TypeMirror[]::new);
            return ctx.types().getDeclaredType(app.getErasure(), args);
        }
        throw new IllegalStateException("Unknown PortType: " + template);
    }
}
