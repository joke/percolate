package io.github.joke.percolate.spi;

import com.palantir.javapoet.CodeBlock;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;

/**
 * The single base a developer extends to declare a container in <b>one</b> class (design D5, value-operation-graph
 * §10). The author supplies a type predicate ({@link #matches}), an element extractor ({@link #element}), and the
 * optional kind-local operation snippets it can supply — {@link #iterate}, {@link #collect}, {@link #wrap},
 * {@link #unwrap}, and the same-kind scope-owning {@link #mapPresence}. The base derives candidacy and emits only
 * kind-local plain operations over an explicit {@code Stream<E>} intermediate; no container ever names another kind.
 *
 * <p><b>Kind is emergent from which operations are present.</b> A container that supplies {@link #collect} is a
 * sequence (List, Set, array, Flux); one that omits {@code collect} (supplying {@code wrap}/{@code unwrap}/
 * {@code mapPresence}) is a presence wrapper (Optional, Mono). There is no {@code SequenceContainer} /
 * {@code WrapperContainer} type distinction — the optional-operation set <em>is</em> the capability flag.
 *
 * <p>The per-element transform is <em>not</em> here: it is the kind-free stream {@code map}/{@code flatMap} strategy
 * ({@code StreamMap}), which composes with these over shared {@code Stream} Values. The developer writes no graph or
 * operation logic; register with {@code @AutoService({ExpansionStrategy.class, SourceProjection.class})} like any
 * other strategy.
 *
 * <p>A container is also the source-facing half of element mapping (design D8): it implements {@link SourceProjection}
 * to project a source of <b>its own kind</b> to its intermediate ({@code Cont<X> → Stream<X>}), so the engine can
 * ground a generic {@code Stream<A>} map port against a cross-kind source (a {@code List<X>}/{@code Optional<X>}/…)
 * without naming any kind. Only a container that can be opened (supplies {@link #iterate}) projects; it projects only
 * its own kind, so cross-paradigm bridges are never invented.
 */
public abstract class Container implements ContainerMatch, SourceProjection {

    private static final String ELEMENT_ROLE = "element";
    private static final String SOURCE_ROLE = "source";
    private static final String STREAM_ROLE = "stream";

    /** A unary, inline container snippet ({@code iterate}/{@code collect}/{@code wrap}). */
    @FunctionalInterface
    public interface UnarySnippet {
        /** Render the snippet over its single operand (the container, stream, or scalar expression). */
        CodeBlock render(CodeBlock operand);
    }

    /** The (partial) {@code unwrap} snippet: collapse a wrapper to a scalar under the demanded nullability. */
    @FunctionalInterface
    public interface UnwrapSnippet {
        /** Render the collapse of {@code wrapper} to a scalar under {@code targetNullability}. */
        CodeBlock render(CodeBlock wrapper, Nullability targetNullability);
    }

    /** Whether {@code type} is this container's kind. */
    protected abstract boolean matches(TypeMirror type, ResolveCtx ctx);

    /** The element type of a {@code type} of this container's kind. */
    protected abstract TypeMirror element(TypeMirror type);

    /** Open an element stream over this container ({@code Cont<E> → Stream<E>}); empty when not supported. */
    public Optional<UnarySnippet> iterate() {
        return Optional.empty();
    }

    /** Close a stream into this container ({@code Stream<E> → Cont<E>}, a sequence); empty for a presence wrapper. */
    public Optional<UnarySnippet> collect() {
        return Optional.empty();
    }

    /** Lift a single scalar into this container ({@code E → Cont<E>}); empty when there is no synchronous form. */
    public Optional<UnarySnippet> wrap() {
        return Optional.empty();
    }

    /** Collapse this container to a scalar ({@code Cont<E> → E}, partial); empty for a sequence. */
    public Optional<UnwrapSnippet> unwrap() {
        return Optional.empty();
    }

    /** The same-kind presence-preserving element map ({@code Cont<A> → Cont<B>}, scope-owning); empty otherwise. */
    public Optional<ScopeCodegen> mapPresence() {
        return Optional.empty();
    }

    /**
     * Projects an in-scope {@code source} of this container's kind to its element stream ({@code Cont<X> → Stream<X>})
     * for grounding-by-match only (design D8) — empty for any other source, and empty for a kind that cannot be opened
     * (no {@link #iterate}). The engine widens its match set with this so a generic {@code Stream<A>} port grounds
     * {@code A := X}; the concrete {@code Stream<X>} is then produced by this container's own {@code iterate}.
     */
    @Override
    public final Stream<TypeMirror> project(final TypeMirror source, final ResolveCtx ctx) {
        if (iterate().isEmpty() || !matches(source, ctx)) {
            return Stream.empty();
        }
        return Containers.streamOf(element(source), ctx).stream();
    }

    @Override
    public final Stream<OperationSpec> bridge(final TypeMirror from, final Demand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        final var specs = Stream.<OperationSpec>builder();
        if (matches(to, ctx)) {
            producing(from, to, ctx, specs);
        }
        if (matches(from, ctx)) {
            consuming(from, to, demand, ctx, specs);
        }
        return specs.build();
    }

    private void producing(
            final TypeMirror from,
            final TypeMirror to,
            final ResolveCtx ctx,
            final Stream.Builder<OperationSpec> specs) {
        final var elementOut = element(to);
        collect().ifPresent(close -> Containers.streamOf(elementOut, ctx)
                .ifPresent(streamType -> specs.add(OperationSpec.of(
                        "collect",
                        unary(close),
                        Weights.CONTAINER,
                        List.of(new Port(STREAM_ROLE, streamType, Nullability.NON_NULL)),
                        to,
                        Nullability.NON_NULL))));
        wrap().ifPresent(lift -> specs.add(OperationSpec.of(
                "wrap",
                unary(lift),
                Weights.CONTAINER,
                List.of(new Port(ELEMENT_ROLE, elementOut, Nullability.NON_NULL)),
                to,
                Nullability.NON_NULL)));
        mapPresence().ifPresent(map -> {
            if (matches(from, ctx)) {
                final var child =
                        new ChildScopeSpec(element(from), Nullability.NON_NULL, elementOut, Nullability.NON_NULL);
                specs.add(OperationSpec.mapping(
                        "map",
                        map,
                        Weights.CONTAINER,
                        List.of(new Port(SOURCE_ROLE, from, Nullability.NON_NULL)),
                        to,
                        Nullability.NON_NULL,
                        child));
            }
        });
    }

    private void consuming(
            final TypeMirror from,
            final TypeMirror to,
            final Demand demand,
            final ResolveCtx ctx,
            final Stream.Builder<OperationSpec> specs) {
        final var elementIn = element(from);
        final var sourcePort = List.of(new Port(SOURCE_ROLE, from, Nullability.NON_NULL));
        iterate().ifPresent(open -> {
            if (Containers.isStream(to, ctx) && ctx.types().isSameType(Containers.typeArgument(to, 0), elementIn)) {
                specs.add(OperationSpec.of(
                        "iterate", unary(open), Weights.CONTAINER, sourcePort, to, Nullability.NON_NULL));
            }
        });
        unwrap().ifPresent(collapse -> {
            if (ctx.types().isSameType(to, elementIn)) {
                specs.add(OperationSpec.ofPartial(
                        "unwrap",
                        (OperationCodegen) inputs -> collapse.render(inputs.single(), demand.targetNullness()),
                        Weights.CONTAINER,
                        sourcePort,
                        to,
                        demand.targetNullness()));
            }
        });
    }

    private static OperationCodegen unary(final UnarySnippet snippet) {
        return inputs -> snippet.render(inputs.single());
    }
}
