package io.github.joke.percolate.reactorblocking;

import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.TypeProbe;
import java.util.Optional;
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

    /** Strictly above any realistic non-blocking plan, so blocking is chosen only when nothing lazy can produce. */
    static final int WEIGHT = 1_000;

    /** {@code fqn<arg>} as a concrete declared type, or empty when {@code fqn} is not on the compile classpath. */
    Optional<DeclaredType> declared(final ResolveCtx ctx, final String fqn, final TypeMirror arg) {
        final var element = ctx.elements().getTypeElement(fqn);
        return element == null ? Optional.empty() : Optional.of(ctx.types().getDeclaredType(element, arg));
    }

    /** A target eligible for {@code block()}/{@code single().block()}: a plain reference type, never itself reactive. */
    boolean isBlockableScalar(final TypeMirror type, final ResolveCtx ctx) {
        return type.getKind() == TypeKind.DECLARED
                && !TypeProbe.isType(type, MONO, ctx)
                && !TypeProbe.isType(type, FLUX, ctx);
    }
}
