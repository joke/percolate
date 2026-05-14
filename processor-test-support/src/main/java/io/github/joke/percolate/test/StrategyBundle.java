package io.github.joke.percolate.test;

import io.github.joke.percolate.processor.spi.Bridge;
import io.github.joke.percolate.processor.spi.GroupTarget;
import io.github.joke.percolate.processor.spi.SourceStep;
import java.util.List;
import lombok.Value;

@Value
public class StrategyBundle {

    List<Bridge> bridges;
    List<SourceStep> sourceSteps;
    List<GroupTarget> groupTargets;

    public static StrategyBundle empty() {
        return new StrategyBundle(List.of(), List.of(), List.of());
    }
}
