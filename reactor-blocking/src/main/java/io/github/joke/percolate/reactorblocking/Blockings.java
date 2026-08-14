package io.github.joke.percolate.reactorblocking;

import io.github.joke.percolate.spi.ResolveCtx;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.VisibleForTesting;

// Shared reactor FQNs, a concrete-type builder, and the deliberately high upward-crossing weight for the
// blocking (async-to-sync) bridge strategies. Every blocking edge is weighted strictly above any non-blocking
// alternative so that, whenever a lazy reactive path exists, it always wins on cost — a correctness property
// (deferred vs blocks-at-assembly), not a style one. The weight stays finite, so a blocking edge is still
// chosen when it is the only producer.
@UtilityClass
class Blockings {

    static final String FLUX = "reactor.core.publisher.Flux";
    static final String MONO = "reactor.core.publisher.Mono";
    static final String STREAM = "java.util.stream.Stream";
    static final String OPTIONAL = "java.util.Optional";

    // Strictly above any realistic non-blocking plan, so blocking is chosen only when nothing lazy can produce.
    static final int WEIGHT = 1_000;

    // fqn<arg> as a concrete declared type, or empty when fqn is not on the compile classpath.
    @VisibleForTesting
    static Optional<TypeMirror> declared(final ResolveCtx ctx, final String fqn, final TypeMirror arg) {
        final var element = ctx.typeElementNamed(fqn);
        return element == null ? Optional.empty() : Optional.of(ctx.declaredType(element, arg));
    }

    // source viewed as targetFqn<X> when it is exactly kindFqn<X> with a reference X, else empty — a blocking
    // (async→sync) grounding view for grounding-by-match only, so a JDK element-map port (e.g. Stream<A>) grounds
    // its element type against a reactive source. It only widens the match set; the concrete intermediate is still
    // produced target-driven by the weighted reuse-only blocking bridge, so no eager block is invented. Returns
    // empty for any unrecognised source and names no kind beyond the two requested.
    @VisibleForTesting
    static Stream<TypeMirror> view(
            final TypeMirror source, final String kindFqn, final String targetFqn, final ResolveCtx ctx) {
        if (!isSingleReferenceArgKind(source, kindFqn, ctx)) {
            return Stream.empty();
        }
        final var target = ctx.typeElementNamed(targetFqn);
        return target == null ? Stream.empty() : Stream.of(ctx.declaredType(target, ctx.typeArgument(source, 0)));
    }

    // source is a declared kindFqn<X> with exactly one reference type argument X.
    @VisibleForTesting
    static boolean isSingleReferenceArgKind(final TypeMirror source, final String kindFqn, final ResolveCtx ctx) {
        if (!ctx.isDeclared(source) || !ctx.isType(source, kindFqn)) {
            return false;
        }
        return ctx.typeArgumentCount(source) == 1 && ctx.isReferenceType(ctx.typeArgument(source, 0));
    }

    // A target eligible for block()/single().block(): a plain reference type, never itself reactive.
    @VisibleForTesting
    static boolean isBlockableScalar(final TypeMirror type, final ResolveCtx ctx) {
        return ctx.isDeclared(type) && !ctx.isType(type, MONO) && !ctx.isType(type, FLUX);
    }
}
