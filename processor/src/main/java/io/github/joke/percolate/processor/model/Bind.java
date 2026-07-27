package io.github.joke.percolate.processor.model;

import io.github.joke.percolate.spi.Subject;
import java.util.List;
import lombok.Value;

/**
 * One {@link io.github.joke.percolate.spi.DirectiveSink#bind} call recorded by a reader (design D7 of change
 * {@code decouple-engine-from-strategy-semantics}): a target binding, optionally pinned to a source path (empty for
 * none), carrying the {@link Subject} the reader captured for it — the position a duplicate-target diagnostic
 * underlines.
 */
@Value
public class Bind {
    List<String> targetPath;
    List<String> sourcePath;
    Subject subject;
}
