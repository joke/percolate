package io.github.joke.percolate.spi;

import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;

/**
 * Base for a sequence container (List, Set, array, Flux). A developer declares such a container in <b>one</b>
 * class by supplying only its type predicate ({@link #matches}), its element extractor ({@link #element}), and
 * its stream snippets ({@link ContainerCodegen}). The base derives candidacy and emits the iterate ({@code
 * ENTERING}) / collect ({@code EXITING}) element-scope {@link ExpansionStep}s, attaching itself as the codegen
 * provider; the developer writes no graph or step logic. Register with {@code @AutoService(ExpansionStrategy.class)},
 * exactly like any other strategy.
 */
public abstract class SequenceContainer implements ContainerMatch, ContainerCodegen {

    private static final String ELEMENT_ROLE = "element";

    protected abstract boolean matches(TypeMirror type, ResolveCtx ctx);

    protected abstract TypeMirror element(TypeMirror type);

    /**
     * The single-element wrap snippet (e.g. {@code List.of(x)}), or empty when the container has no synchronous
     * single-element form (arrays). Emitted as a scalar (no element-scope) {@link EdgeCodegen} boundary step.
     */
    protected Optional<EdgeCodegen> singleElementWrap() {
        return Optional.empty();
    }

    @Override
    public final Stream<ExpansionStep> bridge(final TypeMirror from, final TypeMirror to, final ResolveCtx ctx) {
        final var steps = Stream.<ExpansionStep>builder();
        if (matches(to, ctx)) {
            final var elementType = element(to);
            final var elementSlot = new Slot(ELEMENT_ROLE, elementType, Weights.CONTAINER, null);
            steps.add(ExpansionStep.containerBoundary(elementSlot, to, this, ElementScope.EXITING, Weights.CONTAINER));
            singleElementWrap()
                    .ifPresent(wrap -> steps.add(
                            ExpansionStep.boundary(java.util.List.of(elementSlot), to, wrap, Weights.CONTAINER)));
        }
        if (matches(from, ctx)) {
            final var elementType = element(from);
            final var containerSlot = new Slot(ELEMENT_ROLE, from, Weights.CONTAINER, null);
            steps.add(ExpansionStep.containerBoundary(
                    containerSlot, elementType, this, ElementScope.ENTERING, Weights.CONTAINER));
        }
        return steps.build();
    }
}
