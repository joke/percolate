package io.github.joke.percolate.spi;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;

/**
 * Base for a sequence container (List, Set, array, Flux). A developer declares such a container in <b>one</b>
 * class by supplying only its type predicate ({@link #matches}), its element extractor ({@link #element}), and
 * its stream snippets ({@link ContainerCodegen}). The base derives candidacy and emits only <b>kind-local plain</b>
 * operations over an explicit {@code Stream<T>} intermediate (design D7):
 *
 * <ul>
 *   <li><b>iterate</b> ({@code Cont<E> → Stream<E>}, {@code .stream()}) when {@code from} is this container kind
 *       and the demand is {@code Stream<E>};</li>
 *   <li><b>collect</b> ({@code Stream<E> → Seq<E>}) when the demand is this container kind;</li>
 *   <li>a single-element <b>wrap</b> ({@link #singleElementWrap}, e.g. {@code List.of(x)}) when present.</li>
 * </ul>
 *
 * The per-element transform is <em>not</em> here: it is the kind-free stream {@code map}/{@code flatMap} strategy,
 * which composes with these over shared {@code Stream} Values. No container knows another kind. The developer
 * writes no graph or operation logic. Register with {@code @AutoService(ExpansionStrategy.class)} like any other.
 */
public abstract class SequenceContainer implements ContainerMatch, ContainerCodegen {

    private static final String ELEMENT_ROLE = "element";
    private static final String SOURCE_ROLE = "source";
    private static final String STREAM_ROLE = "stream";

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
        final var specs = Stream.<OperationSpec>builder();
        if (matches(to, ctx)) {
            final var elementOut = element(to);
            Containers.streamOf(elementOut, ctx)
                    .ifPresent(streamType -> specs.add(OperationSpec.of(
                            collectCodegen(),
                            Weights.CONTAINER,
                            List.of(new Port(STREAM_ROLE, streamType, Nullability.NON_NULL)),
                            to,
                            Nullability.NON_NULL)));
            singleElementWrap()
                    .ifPresent(wrap -> specs.add(OperationSpec.of(
                            wrap,
                            Weights.CONTAINER,
                            List.of(new Port(ELEMENT_ROLE, elementOut, Nullability.NON_NULL)),
                            to,
                            Nullability.NON_NULL)));
        }
        if (matches(from, ctx) && opensStreamOf(to, element(from), ctx)) {
            specs.add(OperationSpec.of(
                    iterateCodegen(),
                    Weights.CONTAINER,
                    List.of(new Port(SOURCE_ROLE, from, Nullability.NON_NULL)),
                    to,
                    Nullability.NON_NULL));
        }
        return specs.build();
    }

    private OperationCodegen iterateCodegen() {
        return (vars, inputs) -> iterate(inputs.single());
    }

    private OperationCodegen collectCodegen() {
        return (vars, inputs) -> collect(inputs.single());
    }

    /** Whether {@code to} is {@code Stream<element>} — the target of iterating this container. */
    private static boolean opensStreamOf(final TypeMirror to, final TypeMirror element, final ResolveCtx ctx) {
        return Containers.isStream(to, ctx) && ctx.types().isSameType(Containers.typeArgument(to, 0), element);
    }
}
