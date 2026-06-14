package io.github.joke.percolate.spi;

/**
 * Convenience mixin for container strategies. A container decides per {@code (source, target)} type pair like any
 * {@link CombinatorialMatch}, but the specs it emits are kind-local container operations over an explicit
 * {@code Stream<E>} intermediate (a plain {@code iterate}/{@code collect}/{@code wrap}/{@code unwrap}, or a
 * same-kind scope-owning {@code mapPresence}). The single {@link Container} base supplies the {@link #bridge}
 * implementation; an author declares a container by extending it and providing only its type predicate, element
 * extractor, and the operation snippets it supports. Mixing this in keeps a container a single
 * {@link ExpansionStrategy} to the loader — no kind-ordering is introduced.
 */
public interface ContainerMatch extends CombinatorialMatch {}
