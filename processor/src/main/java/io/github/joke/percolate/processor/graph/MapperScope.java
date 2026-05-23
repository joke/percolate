package io.github.joke.percolate.processor.graph;

import lombok.Value;

@Value
public class MapperScope implements Scope {
    static final MapperScope INSTANCE = new MapperScope();

    @Override
    public String encode() {
        return "mapper";
    }
}
