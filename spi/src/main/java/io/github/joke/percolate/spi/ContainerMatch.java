package io.github.joke.percolate.spi;

/**
 * Convenience mixin for container strategies. A container decides per {@code (source, target)} type pair like any
 * {@link CombinatorialMatch}, but the steps it emits are element-scope {@link Intent#BOUNDARY} steps (iterate /
 * collect / unwrap / wrap) carrying an {@link ElementScope}, with the container itself as the {@link Codegen}
 * provider on the realised edge. The concrete bases {@link SequenceContainer} and {@link WrapperContainer} supply
 * the per-paradigm {@link #bridge} implementation; an author declares a container by extending one of them and
 * providing only its type predicate, element extractor, and codegen snippets. Mixing this in keeps a container a
 * single {@link ExpansionStrategy} to the loader — no kind-ordering is introduced.
 */
public interface ContainerMatch extends CombinatorialMatch {}
