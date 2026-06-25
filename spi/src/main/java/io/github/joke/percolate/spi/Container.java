package io.github.joke.percolate.spi;

import com.palantir.javapoet.CodeBlock;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * The single base a developer extends to declare a container in <b>one</b> class (design D5/D8). The author supplies a
 * type predicate ({@link #matches}), an element extractor ({@link #element}), this kind's declared erasure
 * ({@link #kindErasure}), its <b>element-sequence intermediate</b> erasure ({@link #intermediateErasure} — JDK
 * containers declare {@code java.util.stream.Stream}; a reactive container declares {@code Flux}), and the optional
 * kind-local operation snippets it supports — {@link #iterate}, {@link #collect}, {@link #wrap}, {@link #unwrap}, and
 * the same-kind scope-owning {@link #mapPresence}. The base is fully <b>target-driven</b>: every operation it emits is
 * keyed on the demanded target alone (no candidate is read), and it names no other container kind.
 *
 * <p><b>Kind is emergent from which operations are present.</b> A container that supplies {@link #collect} is a
 * sequence (List, Set, array, Flux); one that omits {@code collect} (supplying {@code wrap}/{@code unwrap}/
 * {@code mapPresence}) is a presence wrapper (Optional, Mono). There is no {@code SequenceContainer} /
 * {@code WrapperContainer} type distinction — the optional-operation set <em>is</em> the capability flag.
 *
 * <p>The per-element transform over the intermediate is <em>not</em> here: it is the generic stream {@code map}/
 * {@code flatMap} strategy ({@code StreamMap}), which composes with these over the declared intermediate. The same-kind
 * {@link #mapPresence} that a wrapper supplies is emitted here as a <b>functor lift</b> ({@code F<B> ← F<A>}, child
 * {@code A → B}) over a type-variable port grounded by the engine. The developer writes no graph or operation logic;
 * register with {@code @AutoService({ExpansionStrategy.class, SourceProjection.class})} like any other strategy.
 *
 * <p>A container is also the source-facing half of element mapping: it implements {@link SourceProjection} to project
 * a source of <b>its own kind</b> to its intermediate ({@code Cont<X> → Intermediate<X>}), so the engine can ground a
 * generic intermediate-map port against a cross-kind source without naming any kind. Only a container that can be
 * opened (supplies {@link #iterate}) projects; it projects only its own kind, so cross-paradigm bridges are never
 * invented.
 */
public abstract class Container implements ExpansionStrategy, SourceProjection {

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

    /** This kind's declared erasure ({@code List}/{@code Set}/{@code Optional}/{@code Flux}…); empty for arrays. */
    protected abstract Optional<TypeElement> kindErasure(ResolveCtx ctx);

    /** This container's element-sequence intermediate erasure (JDK = {@code java.util.stream.Stream}). */
    protected abstract TypeElement intermediateErasure(ResolveCtx ctx);

    /** Build this container's kind over {@code element}, or empty when not formable (e.g. a primitive element). */
    protected Optional<TypeMirror> containerOf(final TypeMirror element, final ResolveCtx ctx) {
        if (!Containers.isReferenceType(element)) {
            return Optional.empty();
        }
        return kindErasure(ctx).map(erasure -> ctx.types().getDeclaredType(erasure, element));
    }

    /** Open an element stream over this container ({@code Cont<E> → Intermediate<E>}); empty when not supported. */
    public Optional<UnarySnippet> iterate() {
        return Optional.empty();
    }

    /** Close the intermediate into this container ({@code Intermediate<E> → Cont<E>}, a sequence); empty for a wrapper. */
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
     * Target-driven emission (design D1): keyed only on the demanded target. When the target is this kind it offers
     * {@code collect}/{@code wrap}/{@code map}; when the target is this container's intermediate it offers
     * {@code iterate} (from its own kind); for any scalar target a wrapper offers a partial {@code unwrap}. The driver
     * sources each port and prunes the unreachable ones — no candidate is read.
     */
    @Override
    public final Stream<OperationSpec> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        final var specs = Stream.<OperationSpec>builder();
        if (matches(to, ctx)) {
            produceMyKind(to, ctx, specs);
        }
        if (isIntermediate(to, ctx)) {
            iterateInto(to, ctx, specs);
        }
        unwrapInto(to, demand, ctx, specs);
        return specs.build();
    }

    /**
     * Projects an in-scope {@code source} of this container's kind to its intermediate ({@code Cont<X> →
     * Intermediate<X>}) for grounding-by-match only (design D8) — empty for any other source, and empty for a kind that
     * cannot be opened (no {@link #iterate}). The engine widens its match set with this so a generic intermediate-map
     * port grounds; the concrete intermediate is then produced by this container's own {@code iterate}.
     */
    @Override
    public final Stream<TypeMirror> project(final TypeMirror source, final ResolveCtx ctx) {
        if (iterate().isEmpty() || !matches(source, ctx)) {
            return Stream.empty();
        }
        return intermediateOf(element(source), ctx).stream();
    }

    private void produceMyKind(final TypeMirror to, final ResolveCtx ctx, final Stream.Builder<OperationSpec> specs) {
        final var elementOut = element(to);
        collect().ifPresent(close -> intermediateOf(elementOut, ctx)
                .ifPresent(intermediate -> specs.add(OperationSpec.of(
                        "collect",
                        unary(close),
                        Weights.CONTAINER,
                        List.of(new Port(STREAM_ROLE, intermediate, Nullability.NON_NULL)),
                        to,
                        Nullability.NON_NULL))));
        wrap().ifPresent(lift -> specs.add(OperationSpec.of(
                "wrap",
                unary(lift),
                Weights.CONTAINER,
                List.of(new Port(ELEMENT_ROLE, elementOut, Nullability.NON_NULL)),
                to,
                Nullability.NON_NULL)));
        mapPresence().ifPresent(map -> kindErasure(ctx).ifPresent(erasure -> {
            final var template = PortType.app(erasure, List.of(PortType.variable(0)));
            final var port = new Port(SOURCE_ROLE, erasure.asType(), Nullability.NON_NULL, template);
            final var child =
                    ChildScopeSpec.lifted(PortType.variable(0), Nullability.NON_NULL, elementOut, Nullability.NON_NULL);
            specs.add(OperationSpec.mapping(
                    "map", map, Weights.CONTAINER, List.of(port), to, Nullability.NON_NULL, child));
        }));
    }

    private void iterateInto(final TypeMirror to, final ResolveCtx ctx, final Stream.Builder<OperationSpec> specs) {
        iterate().ifPresent(open -> containerOf(intermediateElement(to), ctx)
                .ifPresent(source -> specs.add(OperationSpec.of(
                        "iterate",
                        unary(open),
                        Weights.CONTAINER,
                        List.of(new Port(SOURCE_ROLE, source, Nullability.NON_NULL)),
                        to,
                        Nullability.NON_NULL))));
    }

    private void unwrapInto(
            final TypeMirror to,
            final ProduceDemand demand,
            final ResolveCtx ctx,
            final Stream.Builder<OperationSpec> specs) {
        // unwrap is a consuming operation whose source (the wrapper) is structurally larger than its output: its port
        // is therefore reuse-only (Port.reuse) — the driver binds an in-scope Cont<to> source or the op does not apply,
        // never minting one. That is what keeps it bounded (you never wrap a value just to unwrap it) and matches the
        // former candidate-keyed semantics (unwrap fired only against an existing wrapper source).
        unwrap().ifPresent(collapse -> containerOf(to, ctx)
                .ifPresent(source -> specs.add(OperationSpec.ofPartial(
                        "unwrap",
                        (OperationCodegen) inputs -> collapse.render(inputs.single(), demand.targetNullness()),
                        Weights.CONTAINER,
                        List.of(Port.reuse(SOURCE_ROLE, source, Nullability.NON_NULL)),
                        to,
                        demand.targetNullness()))));
    }

    // ---- intermediate derivation (from the author-declared intermediateErasure; names no kind) ----------------

    private boolean isIntermediate(final TypeMirror type, final ResolveCtx ctx) {
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        final var types = ctx.types();
        return types.isSameType(
                types.erasure(type), types.erasure(intermediateErasure(ctx).asType()));
    }

    private static TypeMirror intermediateElement(final TypeMirror intermediate) {
        return ((DeclaredType) intermediate).getTypeArguments().get(0);
    }

    private Optional<TypeMirror> intermediateOf(final TypeMirror element, final ResolveCtx ctx) {
        if (!Containers.isReferenceType(element)) {
            return Optional.empty();
        }
        return Optional.of(ctx.types().getDeclaredType(intermediateErasure(ctx), element));
    }

    private static OperationCodegen unary(final UnarySnippet snippet) {
        return inputs -> snippet.render(inputs.single());
    }
}
