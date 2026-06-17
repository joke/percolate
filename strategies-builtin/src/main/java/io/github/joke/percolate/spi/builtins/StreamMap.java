package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.CodeBlock;
import io.github.joke.percolate.spi.ChildScopeSpec;
import io.github.joke.percolate.spi.CombinatorialMatch;
import io.github.joke.percolate.spi.Containers;
import io.github.joke.percolate.spi.Demand;
import io.github.joke.percolate.spi.ExpansionStrategy;
import io.github.joke.percolate.spi.Nullability;
import io.github.joke.percolate.spi.OperationSpec;
import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.ResolveCtx;
import io.github.joke.percolate.spi.ScopeCodegen;
import io.github.joke.percolate.spi.Weights;
import java.util.List;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.NoArgsConstructor;

/**
 * The generic, kind-free element transform over a {@code Stream<T>} (design D7). Given a demand for
 * {@code Stream<B>} and any candidate with a stream-element {@code A} ({@link Containers#streamElement}: a
 * Collection / array / Optional / Stream), it offers two scope-owning operations whose child scope is the
 * per-element plan:
 *
 * <ul>
 *   <li><b>map</b> ({@code Stream<A> → Stream<B>}, child {@code A → B}) — {@code stream.map(a -> …)};</li>
 *   <li><b>flatMap</b> ({@code Stream<A> → Stream<B>}, child {@code A → Stream<B>}) — {@code stream.flatMap(a -> …)},
 *       which is how a wrapper element (its {@code iterate} yields a 0-or-1 stream) is flattened / dropped.</li>
 * </ul>
 *
 * It names no container kind; the source {@code Stream<A>} port is produced by a container's own {@code iterate},
 * so cross-kind composition and flatten emerge from the graph rather than from any multi-kind composer.
 */
@AutoService(ExpansionStrategy.class)
@NoArgsConstructor
public final class StreamMap implements CombinatorialMatch {

    private static final String SOURCE_ROLE = "stream";
    private static final ScopeCodegen MAP =
            (operand, var, body) -> CodeBlock.of("$L.map($N -> $L)", operand, var, body);
    private static final ScopeCodegen FLAT_MAP =
            (operand, var, body) -> CodeBlock.of("$L.flatMap($N -> $L)", operand, var, body);

    @Override
    public Stream<OperationSpec> bridge(final TypeMirror from, final Demand demand, final ResolveCtx ctx) {
        final var to = demand.targetType();
        if (!Containers.isStream(to, ctx)) {
            return Stream.empty();
        }
        final var elementIn = Containers.streamElement(from, ctx).orElse(null);
        if (elementIn == null) {
            return Stream.empty();
        }
        final var elementOut = Containers.typeArgument(to, 0);
        final var sourceStream = Containers.streamOf(elementIn, ctx).orElse(null);
        final var elementStream = Containers.streamOf(elementOut, ctx).orElse(null);
        if (sourceStream == null || elementStream == null) {
            return Stream.empty();
        }
        // Degenerate self-map: the source stream equals the demand, so a container's own `iterate` already
        // produces it (e.g. List<Optional<A>> → Stream<Optional<A>>). Emitting here would be a Stream<X>→Stream<X>
        // identity loop that pollutes the graph and the diagnostics.
        if (ctx.types().isSameType(sourceStream, to)) {
            return Stream.empty();
        }
        final var ports = List.of(new Port(SOURCE_ROLE, sourceStream, Nullability.NON_NULL));
        final var mapChild = new ChildScopeSpec(elementIn, Nullability.NON_NULL, elementOut, Nullability.NON_NULL);
        final var flatMapChild =
                new ChildScopeSpec(elementIn, Nullability.NON_NULL, elementStream, Nullability.NON_NULL);
        return Stream.of(
                OperationSpec.mapping("map", MAP, Weights.CONTAINER, ports, to, Nullability.NON_NULL, mapChild),
                OperationSpec.mapping(
                        "flatMap", FLAT_MAP, Weights.CONTAINER, ports, to, Nullability.NON_NULL, flatMapChild));
    }
}
