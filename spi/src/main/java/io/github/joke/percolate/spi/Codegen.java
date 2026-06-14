package io.github.joke.percolate.spi;

/**
 * Marker for the codegen handle attached to an {@code Operation}. A plain operation carries an
 * {@link OperationCodegen} (it renders its expression from its incoming port values); a scope-owning operation — a
 * container element mapping or a presence {@code mapPresence} — carries a {@link ScopeCodegen} that weaves around
 * the rendered child plan. Container kind-local snippets ({@code iterate}/{@code collect}/{@code wrap}/{@code unwrap})
 * are wrapped into {@link OperationCodegen}s by the {@link Container} base. The composer reads the handle off the
 * operation and asks it to render, holding no container syntax itself.
 */
public interface Codegen {}
