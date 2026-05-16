package io.github.joke.percolate.processor;

import lombok.Value;

import java.util.Map;

@Value
public final class ProcessorOptions {
    boolean debugGraphs;

    static ProcessorOptions from(final Map<String, String> options) {
        return new ProcessorOptions("true".equalsIgnoreCase(options.getOrDefault("percolate.debug.graphs", "false")));
    }
}
