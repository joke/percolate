package io.github.joke.percolate.processor.stages.expand;

/**
 * A description of a single intended graph mutation. {@code Delta} values are produced by pure
 * {@link GroupExpander}s and interpreted by the {@link Applier} — the only class that actually mutates the
 * graph. This data/interpretation split keeps expanders side-effect-free and concentrates every mutation in
 * one auditable place.
 *
 * <p>The taxonomy is a visitor-based sum type (the Java 11 substitute for sealed types): adding a new variant
 * forces a compile error in every {@link Visitor} implementation, giving exhaustiveness for free.
 */
public interface Delta {

    <R> R accept(Visitor<R> visitor);

    interface Visitor<R> {
        R visitAddNode(AddNode delta);

        R visitAddEdge(AddEdge delta);

        R visitTypeNode(TypeNode delta);

        R visitAddGroup(AddGroup delta);
    }
}
