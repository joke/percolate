package io.github.joke.percolate.spi;

import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;

/**
 * Base for a sequence container (List, Set, array, Flux). A developer declares such a container in <b>one</b>
 * class by supplying only its type predicate ({@link #matches}), its element extractor ({@link #element}), and
 * its stream snippets ({@link ContainerCodegen}). The base derives candidacy and emits the iterate/collect
 * {@link BridgeStep}s, attaching itself as the codegen provider; the developer writes no graph or {@code BridgeStep}
 * logic. Register with {@code @AutoService(Bridge.class)}, exactly like any other {@link Bridge}.
 */
public abstract class SequenceContainer implements Bridge, ContainerCodegen {

    protected abstract boolean matches(TypeMirror type, ResolveCtx ctx);

    protected abstract TypeMirror element(TypeMirror type);

    /**
     * The single-element wrap snippet (e.g. {@code List.of(x)}), or empty when the container has no synchronous
     * single-element form (arrays). Emitted as a {@code PRESERVING} scalar {@link EdgeCodegen} step.
     */
    protected Optional<EdgeCodegen> singleElementWrap() {
        return Optional.empty();
    }

    public ContainerCodegen streamCodegen() {
        return this;
    }

    public Optional<LoopContainerCodegen> loopCodegen() {
        return Optional.empty();
    }

    @Override
    public final Stream<BridgeStep> bridge(final TypeMirror from, final TypeMirror to, final ResolveCtx ctx) {
        final var steps = Stream.<BridgeStep>builder();
        if (matches(to, ctx)) {
            final var elementType = element(to);
            steps.add(new BridgeStep(elementType, to, Weights.CONTAINER, this, ScopeTransition.EXITING, "element"));
            singleElementWrap().ifPresent(wrap -> steps.add(new BridgeStep(elementType, to, Weights.CONTAINER, wrap)));
        }
        if (matches(from, ctx)) {
            final var elementType = element(from);
            steps.add(new BridgeStep(from, elementType, Weights.CONTAINER, this, ScopeTransition.ENTERING, "element"));
        }
        return steps.build();
    }
}
