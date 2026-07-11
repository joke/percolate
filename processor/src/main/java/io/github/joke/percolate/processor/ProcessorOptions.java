package io.github.joke.percolate.processor;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;

@Value
public class ProcessorOptions {

    public static final String DEBUG_GRAPHS = "percolate.debug.graphs";
    public static final String NULLABLE_ANNOTATIONS = "percolate.nullable.annotations";
    public static final String LOCALS_FINAL = "percolate.locals.final";
    public static final String LOCALS_VAR = "percolate.locals.var";
    public static final String PARAMETERS_FINAL = "percolate.parameters.final";
    public static final String METHODS_FINAL = "percolate.methods.final";
    public static final String CLASSES_FINAL = "percolate.classes.final";
    public static final String DOC_TAGS = "percolate.docTags";
    public static final String TIME_ZONE = "percolate.time.zone";

    boolean debugGraphs;
    Set<String> customNullableAnnotations;
    boolean localsFinal;
    boolean localsVar;
    boolean parametersFinal;
    boolean methodsFinal;
    boolean classesFinal;
    boolean docTags;
    Optional<String> timeZone;

    @Builder
    public ProcessorOptions(
            final boolean debugGraphs,
            final Set<String> customNullableAnnotations,
            final boolean localsFinal,
            final boolean localsVar,
            final boolean parametersFinal,
            final boolean methodsFinal,
            final boolean classesFinal,
            final boolean docTags,
            final Optional<String> timeZone) {
        this.debugGraphs = debugGraphs;
        this.customNullableAnnotations = Set.copyOf(customNullableAnnotations);
        this.localsFinal = localsFinal;
        this.localsVar = localsVar;
        this.parametersFinal = parametersFinal;
        this.methodsFinal = methodsFinal;
        this.classesFinal = classesFinal;
        this.docTags = docTags;
        this.timeZone = timeZone;
    }

    static ProcessorOptions from(final Map<String, String> options) {
        final var nullableRaw = options.get(NULLABLE_ANNOTATIONS);
        final Set<String> nullable;
        if (nullableRaw == null || nullableRaw.isEmpty()) {
            nullable = Set.of();
        } else {
            nullable = Arrays.stream(nullableRaw.split(","))
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        }
        return ProcessorOptions.builder()
                .debugGraphs(flag(options, DEBUG_GRAPHS))
                .customNullableAnnotations(nullable)
                .localsFinal(flag(options, LOCALS_FINAL))
                .localsVar(flag(options, LOCALS_VAR))
                .parametersFinal(flag(options, PARAMETERS_FINAL))
                .methodsFinal(flag(options, METHODS_FINAL))
                .classesFinal(flag(options, CLASSES_FINAL))
                .docTags(flag(options, DOC_TAGS))
                .timeZone(Optional.ofNullable(options.get(TIME_ZONE)))
                .build();
    }

    private static boolean flag(final Map<String, String> options, final String key) {
        return "true".equalsIgnoreCase(options.getOrDefault(key, "false"));
    }
}
