package io.github.joke.percolate.reactorblocking;

import io.github.joke.percolate.spi.Containers;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.TypeProbe;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import lombok.experimental.UtilityClass;

/**
 * Shared reactor FQNs, a concrete-type builder, and the deliberately high upward-crossing weight for the blocking
 * (async-to-sync) bridge strategies. Every blocking edge is weighted strictly above any non-blocking alternative so
 * that, whenever a lazy reactive path exists, it always wins on cost — a correctness property (deferred vs
 * blocks-at-assembly), not a style one. The weight is finite (well below {@code Weights.SENTINEL_UNREALISED}), so a
 * blocking edge is still chosen when it is the only producer.
 */
@UtilityClass
class Blockings {

    static final String FLUX = "reactor.core.publisher.Flux";
    static final String MONO = "reactor.core.publisher.Mono";
    static final String STREAM = "java.util.stream.Stream";
    static final String OPTIONAL = "java.util.Optional";

    /** Strictly above any realistic non-blocking plan, so blocking is chosen only when nothing lazy can produce. */
    static final int WEIGHT = 1_000;

    /** {@code fqn<arg>} as a concrete declared type, or empty when {@code fqn} is not on the compile classpath. */
    Optional<DeclaredType> declared(final ResolveCtx ctx, final String fqn, final TypeMirror arg) {
        final var element = ctx.elements().getTypeElement(fqn);
        return element == null ? Optional.empty() : Optional.of(ctx.types().getDeclaredType(element, arg));
    }

    /**
     * {@code source} viewed as {@code targetFqn<X>} when it is exactly {@code kindFqn<X>} with a reference {@code X},
     * else empty — a blocking (async→sync) <b>grounding view</b> for grounding-by-match only, so a JDK element-map
     * port (e.g. {@code Stream<A>}) grounds its element type against a reactive source. It only widens the match set;
     * the concrete intermediate is still produced target-driven by the weighted reuse-only blocking bridge, so no
     * eager block is invented. Returns empty for any unrecognised source and names no kind beyond the two requested.
     */
    Stream<TypeMirror> view(
            final TypeMirror source, final String kindFqn, final String targetFqn, final ResolveCtx ctx) {
        if (source.getKind() != TypeKind.DECLARED || !TypeProbe.isType(source, kindFqn, ctx)) {
            return Stream.empty();
        }
        final var args = ((DeclaredType) source).getTypeArguments();
        if (args.size() != 1 || !Containers.isReferenceType(args.get(0))) {
            return Stream.empty();
        }
        final var target = ctx.elements().getTypeElement(targetFqn);
        return target == null ? Stream.empty() : Stream.of(ctx.types().getDeclaredType(target, args.get(0)));
    }

    /** A target eligible for {@code block()}/{@code single().block()}: a plain reference type, never itself reactive. */
    boolean isBlockableScalar(final TypeMirror type, final ResolveCtx ctx) {
        return type.getKind() == TypeKind.DECLARED
                && !TypeProbe.isType(type, MONO, ctx)
                && !TypeProbe.isType(type, FLUX, ctx);
    }
}
