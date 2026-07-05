package io.github.joke.percolate.reactor;

import io.github.joke.percolate.spi.ResolveCtx;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;
import lombok.experimental.UtilityClass;

/** Shared reactor FQNs and a small concrete-type builder for the reactor bridge strategies. */
@UtilityClass
class Reactors {

    static final String FLUX = "reactor.core.publisher.Flux";
    static final String MONO = "reactor.core.publisher.Mono";

    /** {@code fqn<arg>} as a concrete declared type, or empty when {@code fqn} is not on the compile classpath. */
    Optional<TypeMirror> declared(final ResolveCtx ctx, final String fqn, final TypeMirror arg) {
        final var element = ctx.typeElementNamed(fqn);
        return element == null ? Optional.empty() : Optional.of(ctx.declaredType(element, arg));
    }
}
