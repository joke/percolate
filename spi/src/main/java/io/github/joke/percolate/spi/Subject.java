package io.github.joke.percolate.spi;

/**
 * An opaque positioning handle for a diagnostic (design D14 of change {@code decouple-engine-from-strategy-semantics}):
 * where the IDE underlines, kept separate from which unit of work a diagnostic is attributed to. A strategy or reader
 * never builds one directly — it receives one from a structured input and only ever hands it back unchanged, e.g.
 * inside a refusal. Construct one only via {@link Subjects}.
 */
public interface Subject {}
