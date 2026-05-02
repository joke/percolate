package io.github.joke.percolate.processor;

import java.util.Map;
import lombok.Value;

@Value
final class ProcessorOptions {
    boolean debugGraphs;

    static ProcessorOptions from(final Map<String, String> options) {
        return new ProcessorOptions("true".equalsIgnoreCase(options.getOrDefault("percolate.debug.graphs", "false")));
    }
}
