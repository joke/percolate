package io.github.joke.percolate.reactor;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.ChildScopeSpec;
import io.github.joke.percolate.spi.Containers;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ProduceDemand;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.ScopeCodegen;
import io.github.joke.percolate.spi.TypeProbe;
import io.github.joke.percolate.spi.Weights;
import io.github.joke.percolate.spi.types.TypeRef;
import java.util.List;
import java.util.stream.Stream;
import lombok.NoArgsConstructor;

/**
 * The generic, kind-free element transform over a {@code Flux<T>} — the reactive twin of {@code StreamMap}, keyed to
 * {@code reactor.core.publisher.Flux} instead of {@code java.util.stream.Stream} (design D3/D5). Given a demand for
 * {@code Flux<B>} it offers two scope-owning operations whose input port is the type-variable {@code Flux<A>} and whose
 * child scope is the per-element plan:
 *
 * <ul>
 *   <li><b>map</b> ({@code Flux<A> → Flux<B>}, child {@code A → B}) — {@code flux.map(a -> …)};</li>
 *   <li><b>flatMap</b> ({@code Flux<A> → Flux<B>}, child {@code A → Flux<B>}) — {@code flux.flatMap(a -> …)}.</li>
 * </ul>
 *
 * <p>It reads no candidate: {@code A} is grounded by the engine by matching the {@code Flux<A>} port against an in-scope
 * source — directly when a {@code Flux<X>} source exists, or via {@code MonoContainer}'s {@code SourceProjection} when
 * only a {@code Mono<X>} source exists. The engine cannot tell {@code flux.map} from {@code stream.map}.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class FluxMap implements ExpansionStrategy {

    private static final String FLUX = "reactor.core.publisher.Flux";
    private static final String SOURCE_ROLE = "flux";
    private static final ScopeCodegen MAP =
            (operand, var, body) -> CodeBlock.of("$L.map($N -> $L)", operand, var, body);
    private static final ScopeCodegen FLAT_MAP =
            (operand, var, body) -> CodeBlock.of("$L.flatMap($N -> $L)", operand, var, body);

    @Override
    public Stream<OperationSpec> expand(final ProduceDemand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        if (!TypeProbe.isType(to, FLUX, ctx)) {
            return Stream.empty();
        }
        final var fluxErasure = ctx.elements().getTypeElement(FLUX);
        if (fluxErasure == null) {
            return Stream.empty();
        }
        final var elementOut = Containers.typeArgument(to, 0);
        final var template = TypeRef.declared(FLUX, TypeRef.variable("V0"));
        final var ports = List.of(new Port(SOURCE_ROLE, fluxErasure.asType(), Nullability.NON_NULL, template));
        final var mapChild =
                ChildScopeSpec.lifted(TypeRef.variable("V0"), Nullability.NON_NULL, elementOut, Nullability.NON_NULL);
        final var flatMapChild =
                ChildScopeSpec.lifted(TypeRef.variable("V0"), Nullability.NON_NULL, to, Nullability.NON_NULL);
        return Stream.of(
                OperationSpec.mapping("map", MAP, Weights.CONTAINER, ports, to, Nullability.NON_NULL, mapChild),
                OperationSpec.mapping(
                        "flatMap", FLAT_MAP, Weights.CONTAINER, ports, to, Nullability.NON_NULL, flatMapChild));
    }
}
