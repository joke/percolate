package io.github.joke.percolate.spi;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;

/**
 * Base for a sequence container (List, Set, array, Flux). A developer declares such a container in <b>one</b>
 * class by supplying only its type predicate ({@link #matches}), its element extractor ({@link #element}), and
 * its stream snippets ({@link ContainerCodegen}). The base derives candidacy and emits:
 *
 * <ul>
 *   <li>an <b>element mapping</b> {@link OperationSpec} (scope-owning) when both source and target are this
 *       container kind ({@code List<A> → List<B>}) — its outer port is the source container, its child scope holds
 *       the per-element transform, and the container itself is the codegen handle (iterate / map / collect);</li>
 *   <li>a <b>wrap</b> {@link OperationSpec} (plain) lifting a single element into the container, when the container
 *       has a synchronous single-element form ({@link #singleElementWrap}).</li>
 * </ul>
 *
 * The developer writes no graph or operation logic. Register with {@code @AutoService(ExpansionStrategy.class)},
 * exactly like any other strategy.
 */
public abstract class SequenceContainer implements ContainerMatch, ContainerCodegen {

    private static final String ELEMENT_ROLE = "element";
    private static final String SOURCE_ROLE = "source";

    protected abstract boolean matches(TypeMirror type, ResolveCtx ctx);

    protected abstract TypeMirror element(TypeMirror type);

    /**
     * The single-element wrap codegen (e.g. {@code List.of(x)}), or empty when the container has no synchronous
     * single-element form (arrays). Emitted as a plain (no child scope) wrap operation.
     */
    protected Optional<OperationCodegen> singleElementWrap() {
        return Optional.empty();
    }

    @Override
    public final Stream<OperationSpec> bridge(final TypeMirror from, final Demand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        if (!matches(to, ctx)) {
            return Stream.empty();
        }
        final var specs = Stream.<OperationSpec>builder();
        final var elementOut = element(to);
        if (matches(from, ctx)) {
            final var elementIn = element(from);
            final var port = new Port(SOURCE_ROLE, from, Nullability.NON_NULL);
            final var child =
                    new ChildScopeSpec(elementIn, Nullability.NON_NULL, elementOut, Nullability.NON_NULL);
            specs.add(OperationSpec.mapping(this, Weights.CONTAINER, List.of(port), to, Nullability.NON_NULL, child));
        }
        singleElementWrap().ifPresent(wrap -> {
            final var port = new Port(ELEMENT_ROLE, elementOut, Nullability.NON_NULL);
            specs.add(OperationSpec.of(wrap, Weights.CONTAINER, List.of(port), to, Nullability.NON_NULL));
        });
        return specs.build();
    }
}
