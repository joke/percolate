package io.github.joke.percolate.processor.graph;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@RequiredArgsConstructor
@ToString(includeFieldNames = false)
public final class SourceRootNode {

    private final String name;
}
