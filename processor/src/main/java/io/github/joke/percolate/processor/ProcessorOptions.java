package io.github.joke.percolate.processor;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Value;

@Value
public class ProcessorOptions {
    boolean debugGraphs;
    Set<String> customNullableAnnotations;

    public ProcessorOptions(final boolean debugGraphs, final Set<String> customNullableAnnotations) {
        this.debugGraphs = debugGraphs;
        this.customNullableAnnotations = Set.copyOf(customNullableAnnotations);
    }

    static ProcessorOptions from(final Map<String, String> options) {
        final var debug = "true".equalsIgnoreCase(options.getOrDefault("percolate.debug.graphs", "false"));
        final var nullableRaw = options.get("percolate.nullable.annotations");
        final Set<String> nullable;
        if (nullableRaw == null || nullableRaw.isEmpty()) {
            nullable = Set.of();
        } else {
            nullable = Arrays.stream(nullableRaw.split(","))
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        }
        return new ProcessorOptions(debug, nullable);
    }
}
