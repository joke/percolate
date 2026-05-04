package io.github.joke.percolate.processor;

public interface Stage {
    void run(MapperContext ctx);
}
