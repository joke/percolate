package io.github.joke.percolate.processor.internal.stages.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.spi.ChildScopeSpec;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.PortType;
import io.github.joke.percolate.spi.ResolveCtx;
import java.util.Map;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Substitutes one binding map across a spec's ports and child scope, producing a fully-concrete {@link OperationSpec}
 * (design D4 of change {@code decompose-engine-stages}, decomposed out of {@code Grounding}'s {@code instantiate}
 * family). {@link #ground} recurses over a {@link PortType.App}'s nested argument shapes — the sole genuine
 * self-recursion in this collaborator, isolated in its spec with a {@code Spy}.
 */
@RequiredArgsConstructor
final class SpecInstantiator {

    private final ResolveCtx ctx;

    /** {@code spec} with every template port and the child scope substituted by {@code bindings}. */
    OperationSpec instantiate(final OperationSpec spec, final Map<Integer, TypeMirror> bindings) {
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
                            childScope.get())
                    .withConsumedOptionKeys(spec.getConsumedOptionKeys())
                    .withMemberRequests(spec.getMemberRequests());
        }
        if (spec.isPartial()) {
            return OperationSpec.ofPartial(
                            spec.getLabel(),
                            spec.getCodegen(),
                            spec.getWeight(),
                            ports,
                            spec.getOutputType(),
                            spec.getOutputNullness())
                    .withConsumedOptionKeys(spec.getConsumedOptionKeys())
                    .withMemberRequests(spec.getMemberRequests());
        }
        return OperationSpec.of(
                        spec.getLabel(),
                        spec.getCodegen(),
                        spec.getWeight(),
                        ports,
                        spec.getOutputType(),
                        spec.getOutputNullness())
                .withConsumedOptionKeys(spec.getConsumedOptionKeys())
                .withMemberRequests(spec.getMemberRequests());
    }

    /** {@code port} with its template substituted by {@code bindings}, or {@code port} unchanged when it has none. */
    Port groundPort(final Port port, final Map<Integer, TypeMirror> bindings) {
        final var template = port.getTemplate();
        if (template == null) {
            return port;
        }
        // Preserve the original port's sourcing mode: grounding produces a concrete port, never resets it to default.
        return new Port(port.getName(), ground(template, bindings), port.getNullness(), null, port.getSourcing());
    }

    /** {@code child} with its element-in/out templates (if any) substituted by {@code bindings}. */
    ChildScopeSpec groundChild(final ChildScopeSpec child, final Map<Integer, TypeMirror> bindings) {
        final var elementIn = groundOr(child.getElementInTemplate(), child.getElementIn(), bindings);
        final var elementOut = groundOr(child.getElementOutTemplate(), child.getElementOut(), bindings);
        return new ChildScopeSpec(elementIn, child.getElementInNullness(), elementOut, child.getElementOutNullness());
    }

    /** {@code concrete} when {@code template} is {@code null}, else {@code template} substituted by {@code bindings}. */
    TypeMirror groundOr(
            final @Nullable PortType template, final TypeMirror concrete, final Map<Integer, TypeMirror> bindings) {
        return template == null ? concrete : ground(template, bindings);
    }

    /** The concrete {@link TypeMirror} {@code template} denotes once every variable is substituted by {@code bindings}. */
    TypeMirror ground(final PortType template, final Map<Integer, TypeMirror> bindings) {
        if (template instanceof PortType.Concrete) {
            return ((PortType.Concrete) template).getType();
        }
        if (template instanceof PortType.Var) {
            return groundVar((PortType.Var) template, bindings);
        }
        // PortType is a closed pseudo-sealed hierarchy (Concrete/Var/App): having excluded the first two, this is App.
        final var app = (PortType.App) template;
        final var args =
                app.getArgs().stream().map(arg -> ground(arg, bindings)).toArray(TypeMirror[]::new);
        return ctx.declaredType(app.getErasure(), args);
    }

    TypeMirror groundVar(final PortType.Var template, final Map<Integer, TypeMirror> bindings) {
        final var bound = bindings.get(template.getIndex());
        if (bound == null) {
            throw new IllegalStateException("Ungrounded type variable while instantiating: " + template);
        }
        return bound;
    }
}
