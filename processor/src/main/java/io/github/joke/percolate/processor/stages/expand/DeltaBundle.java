package io.github.joke.percolate.processor.stages.expand;

import java.util.List;
import lombok.Value;

/**
 * A unit of atomic application. A bundle groups the deltas describing one strategy match (one bridge step,
 * one path-segment resolution, one GroupTarget build). The {@link Applier} accepts every delta in the bundle
 * or none of them: if any {@link AddEdge} would close a cycle, the entire bundle is dropped — which is why a
 * cycle-rejected bridge step leaves no orphan input node behind.
 */
@Value
public class DeltaBundle {
    String origin;
    List<Delta> deltas;
}
