package io.github.joke.percolate.processor.internal.graph;

// A description of one intended bipartite-graph mutation, applied through MapperGraph. Exactly two
// implementations exist — AddValue and AddOperation — produced by pure expanders and interpreted only by the
// expansion Applier (the single mutation site).
public interface GraphDelta {}
