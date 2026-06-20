package io.github.joke.percolate.spi;

import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;

/**
 * The second SPI author interface, parallel to {@link ExpansionStrategy} and deliberately <b>source-facing</b>
 * (design D8). Where an {@code ExpansionStrategy} answers "what produces this <em>target</em>?", a
 * {@code SourceProjection} answers "what other types may this in-scope <em>source</em> be viewed as?" — consulted by
 * the engine <b>only</b> to widen grounding-by-match's match set. It is what lets a collection source feed a generic
 * stream functor-lift: a collection projects to its element stream ({@code List<X>}/{@code Set<X>}/{@code Optional<X>}/
 * {@code X[]} → {@code Stream<X>}), so a type-variable {@code Stream<A>} port grounds {@code A := X}; the concrete
 * {@code Stream<X>} is then produced target-driven by a container's own {@code iterate}.
 *
 * <p>The engine consumes the returned types <b>structurally</b> (it unifies them like any other source type) and
 * <b>names no kind</b>; all type knowledge lives in the implementation. A reactive projector projects its own
 * intermediate ({@code Flux<X>}), so cross-paradigm bridges are never invented. Implementations MUST return an empty
 * stream for a source they do not recognise and MUST NOT receive or traverse the graph. Loaded via {@code ServiceLoader}
 * like {@link ExpansionStrategy}; the {@link Container} middle base implements both interfaces, so a container author
 * still writes a single class.
 */
public interface SourceProjection {

    /** The derived types {@code source} may be viewed as (for grounding only), or {@link Stream#empty()}. */
    Stream<TypeMirror> project(TypeMirror source, ResolveCtx ctx);
}
