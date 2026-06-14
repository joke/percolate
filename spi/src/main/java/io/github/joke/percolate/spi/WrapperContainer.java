package io.github.joke.percolate.spi;

import java.util.List;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;

/**
 * Base for a presence container (Optional, Mono). Like {@link SequenceContainer} the developer supplies only
 * {@link #matches}, {@link #element}, and the snippet methods ({@link WrapperCodegen}); the base derives candidacy
 * and emits only <b>kind-local</b> operations (design D7):
 *
 * <ul>
 *   <li><b>wrap</b> ({@code E → Optional<E>}, plain) lifting a scalar in ({@link #wrap});</li>
 *   <li><b>mapPresence</b> ({@code Optional<A> → Optional<B>}, scope-owning) when {@code from} is this wrapper —
 *       the presence-preserving same-kind element map ({@code opt.map(a -> …)}); a wrapper has <b>no collect</b>,
 *       so it never routes through the sequence stream-collect path;</li>
 *   <li><b>iterate</b> ({@code Optional<E> → Stream<E>}, plain, {@code Optional.stream()}) — the 0-or-1 stream that
 *       realises drop-empties when a sequence flat-maps over it;</li>
 *   <li><b>unwrap</b> ({@code Optional<E> → E}, plain, <b>partial</b>) collapsing to a scalar under the target
 *       nullability ({@link #unwrap}) — partial because it may throw on empty, so the plan-extraction totality rule
 *       prefers any total alternative (e.g. drop-empties) over it.</li>
 * </ul>
 *
 * No container knows another kind; cross-kind composition emerges from shared {@code Stream} Values.
 */
public abstract class WrapperContainer implements ContainerMatch, WrapperCodegen {

    private static final String ELEMENT_ROLE = "element";
    private static final String SOURCE_ROLE = "source";

    protected abstract boolean matches(TypeMirror type, ResolveCtx ctx);

    protected abstract TypeMirror element(TypeMirror type);

    @Override
    public final Stream<OperationSpec> bridge(final TypeMirror from, final Demand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        final var specs = Stream.<OperationSpec>builder();
        if (matches(to, ctx)) {
            final var elementOut = element(to);
            specs.add(OperationSpec.of(
                    wrapCodegen(),
                    Weights.CONTAINER,
                    List.of(new Port(ELEMENT_ROLE, elementOut, Nullability.NON_NULL)),
                    to,
                    Nullability.NON_NULL));
            if (matches(from, ctx)) {
                final var child =
                        new ChildScopeSpec(element(from), Nullability.NON_NULL, elementOut, Nullability.NON_NULL);
                specs.add(OperationSpec.mapping(
                        (ScopeCodegen) this::mapPresence,
                        Weights.CONTAINER,
                        List.of(new Port(SOURCE_ROLE, from, Nullability.NON_NULL)),
                        to,
                        Nullability.NON_NULL,
                        child));
            }
        }
        if (matches(from, ctx)) {
            final var elementIn = element(from);
            final var sourcePort = List.of(new Port(SOURCE_ROLE, from, Nullability.NON_NULL));
            if (Containers.isStream(to, ctx) && ctx.types().isSameType(Containers.typeArgument(to, 0), elementIn)) {
                specs.add(OperationSpec.of(iterateCodegen(), Weights.CONTAINER, sourcePort, to, Nullability.NON_NULL));
            }
            if (ctx.types().isSameType(to, elementIn)) {
                specs.add(OperationSpec.ofPartial(
                        unwrapCodegen(demand.targetNullness()),
                        Weights.CONTAINER,
                        sourcePort,
                        to,
                        demand.targetNullness()));
            }
        }
        return specs.build();
    }

    private OperationCodegen wrapCodegen() {
        return (vars, inputs) -> wrap(inputs.single());
    }

    private OperationCodegen iterateCodegen() {
        return (vars, inputs) -> iterate(inputs.single());
    }

    private OperationCodegen unwrapCodegen(final Nullability targetNullness) {
        return (vars, inputs) -> unwrap(inputs.single(), targetNullness);
    }
}
