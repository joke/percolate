package io.github.joke.percolate.processor.transform;

import io.github.joke.percolate.processor.graph.SourcePropertyNode;
import io.github.joke.percolate.processor.graph.TargetPropertyNode;
import java.util.List;
import lombok.Value;

@Value
public class ResolvedMapping {
    SourcePropertyNode source;
    TargetPropertyNode target;
    List<TransformNode> chain;
}
