package io.github.joke.percolate.spi;

/**
 * Convenience mixin for container strategies. A container decides per {@code (source, target)} type pair like any
 * {@link CombinatorialMatch}, but the specs it emits are container operations: a scope-owning element mapping
 * ({@code List<A> → List<B>}, {@code Optional.map}) whose child scope holds the per-element transform, or a plain
 * wrap/unwrap operation. The concrete bases {@link SequenceContainer} and {@link WrapperContainer} supply the
 * per-paradigm {@link #bridge} implementation; an author declares a container by extending one of them and
 * providing only its type predicate, element extractor, and codegen snippets. Mixing this in keeps a container a
 * single {@link ExpansionStrategy} to the loader — no kind-ordering is introduced.
 */
public interface ContainerMatch extends CombinatorialMatch {}
