package io.github.joke.percolate.processor.internal.stages;

import io.github.joke.percolate.processor.MapperContext;

public interface Stage {
    void run(MapperContext ctx);
}
