package io.github.joke.percolate.spi;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;

/**
 * Base for a presence container (Optional, Mono). Like {@link SequenceContainer} the developer supplies only
 * {@link #matches}, {@link #element}, and the snippet methods ({@link WrapperCodegen}); the base derives candidacy
 * and emits:
 *
 * <ul>
 *   <li>an <b>element mapping</b> {@link OperationSpec} (scope-owning, {@code Optional.map}) when both source and
 *       target are this wrapper kind ({@code Optional<A> → Optional<B>}), with this container as the codegen
 *       handle;</li>
 *   <li>a <b>wrap</b> {@link OperationSpec} (plain) lifting a scalar into the wrapper ({@link #wrap});</li>
 *   <li>an <b>unwrap</b> {@link OperationSpec} (plain) collapsing a synthesised wrapper into a scalar target
 *       ({@link #wrapped}), with this container as the codegen handle (it collapses under the target nullability).</li>
 * </ul>
 *
 * <p>A presence wrapper has <b>no collect step</b>: closing a stream into a 0-or-1 container is a sequence concern.
 */
public abstract class WrapperContainer implements ContainerMatch, WrapperCodegen {

    private static final String ELEMENT_ROLE = "element";
    private static final String SOURCE_ROLE = "source";

    protected abstract boolean matches(TypeMirror type, ResolveCtx ctx);

    protected abstract TypeMirror element(TypeMirror type);

    /**
     * The wrapper type over {@code element} (e.g. {@code Optional<element>}). Used to synthesise the unwrap input
     * from a scalar target, or empty when the wrapper cannot wrap that element here.
     */
    protected abstract Optional<TypeMirror> wrapped(TypeMirror element, ResolveCtx ctx);

    @Override
    public final Stream<OperationSpec> bridge(final TypeMirror from, final Demand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        if (matches(to, ctx)) {
            return wrappingSpecs(from, to, ctx);
        }
        return wrapped(to, ctx)
                .map(wrapperType -> {
                    final var port = new Port(SOURCE_ROLE, wrapperType, Nullability.NON_NULL);
                    return Stream.of(
                            OperationSpec.of(this, Weights.CONTAINER, List.of(port), to, demand.targetNullness()));
                })
                .orElseGet(Stream::empty);
    }

    private Stream<OperationSpec> wrappingSpecs(final TypeMirror from, final TypeMirror to, final ResolveCtx ctx) {
        final var specs = Stream.<OperationSpec>builder();
        final var elementOut = element(to);
        if (matches(from, ctx)) {
            final var elementIn = element(from);
            final var port = new Port(SOURCE_ROLE, from, Nullability.NON_NULL);
            final var child =
                    new ChildScopeSpec(elementIn, Nullability.NON_NULL, elementOut, Nullability.NON_NULL);
            specs.add(OperationSpec.mapping(this, Weights.CONTAINER, List.of(port), to, Nullability.NON_NULL, child));
        }
        final OperationCodegen wrap = (vars, inputs) -> wrap(inputs.single());
        final var wrapPort = new Port(ELEMENT_ROLE, elementOut, Nullability.NON_NULL);
        specs.add(OperationSpec.of(wrap, Weights.CONTAINER, List.of(wrapPort), to, Nullability.NON_NULL));
        return specs.build();
    }
}
