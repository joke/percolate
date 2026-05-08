package io.github.joke.percolate.processor.stages;

import io.github.joke.percolate.processor.MapperContext;

public interface Stage {
    void run(MapperContext ctx);
}
