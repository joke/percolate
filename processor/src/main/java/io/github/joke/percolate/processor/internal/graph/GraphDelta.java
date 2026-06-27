package io.github.joke.percolate.processor.internal.graph;

/**
 * A description of one intended bipartite-graph mutation, applied through {@code MapperGraph}. Exactly two
 * implementations exist — {@link AddValue} and {@link AddOperation} — produced by pure expanders and
 * interpreted only by the expansion {@code Applier} (the single mutation site).
 */
public interface GraphDelta {}
