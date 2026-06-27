package io.github.joke.percolate.processor;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Value;

@Value
public class ProcessorOptions {

    public static final String DEBUG_GRAPHS = "percolate.debug.graphs";
    public static final String NULLABLE_ANNOTATIONS = "percolate.nullable.annotations";
    public static final String LOCALS_FINAL = "percolate.locals.final";
    public static final String LOCALS_VAR = "percolate.locals.var";
    public static final String DOC_TAGS = "percolate.docTags";

    boolean debugGraphs;
    Set<String> customNullableAnnotations;
    boolean localsFinal;
    boolean localsVar;
    boolean docTags;

    public ProcessorOptions(
            final boolean debugGraphs,
            final Set<String> customNullableAnnotations,
            final boolean localsFinal,
            final boolean localsVar,
            final boolean docTags) {
        this.debugGraphs = debugGraphs;
        this.customNullableAnnotations = Set.copyOf(customNullableAnnotations);
        this.localsFinal = localsFinal;
        this.localsVar = localsVar;
        this.docTags = docTags;
    }

    static ProcessorOptions from(final Map<String, String> options) {
        final var debug = flag(options, DEBUG_GRAPHS);
        final var nullableRaw = options.get(NULLABLE_ANNOTATIONS);
        final Set<String> nullable;
        if (nullableRaw == null || nullableRaw.isEmpty()) {
            nullable = Set.of();
        } else {
            nullable = Arrays.stream(nullableRaw.split(","))
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        }
        return new ProcessorOptions(
                debug, nullable, flag(options, LOCALS_FINAL), flag(options, LOCALS_VAR), flag(options, DOC_TAGS));
    }

    private static boolean flag(final Map<String, String> options, final String key) {
        return "true".equalsIgnoreCase(options.getOrDefault(key, "false"));
    }
}
