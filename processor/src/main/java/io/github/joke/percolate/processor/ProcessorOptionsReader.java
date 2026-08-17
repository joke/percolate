package io.github.joke.percolate.processor;

import jakarta.inject.Inject;
import java.util.Map;
import java.util.Set;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;

import static io.github.joke.percolate.processor.ProcessorOptions.CLASSES_FINAL;
import static io.github.joke.percolate.processor.ProcessorOptions.DEBUG_GRAPHS;
import static io.github.joke.percolate.processor.ProcessorOptions.DOC_TAGS;
import static io.github.joke.percolate.processor.ProcessorOptions.LOCALS_FINAL;
import static io.github.joke.percolate.processor.ProcessorOptions.LOCALS_VAR;
import static io.github.joke.percolate.processor.ProcessorOptions.METHODS_FINAL;
import static io.github.joke.percolate.processor.ProcessorOptions.NULLABLE_ANNOTATIONS;
import static io.github.joke.percolate.processor.ProcessorOptions.PARAMETERS_FINAL;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toUnmodifiableSet;

// Reads the raw -A option map into a ProcessorOptions. Split out of that value type by change tighten-
// testability-conventions (design D2): the parsing decisions — a missing flag defaulting to false, an empty
// nullable.annotations meaning "none declared" rather than one empty name — are the part worth testing, and as
// statics on the value type they could not be intercepted. ProcessorOptions is left as plain data.
//
// It parses only the options an engine-internal consumer reads (change add-builder-assembly). A strategy-consumed
// option — time.zone, switch.style, construction.preference — gets no typed field: it travels in the raw map and
// is parsed once, by the strategy that owns its meaning, reached through ResolveCtx.option(key).
@NoArgsConstructor(onConstructor_ = @Inject)
public class ProcessorOptionsReader {

    public ProcessorOptions from(final Map<String, String> options) {
        return ProcessorOptions.builder()
                .debugGraphs(flag(options, DEBUG_GRAPHS))
                .customNullableAnnotations(nullableAnnotations(options))
                .localsFinal(flag(options, LOCALS_FINAL))
                .localsVar(flag(options, LOCALS_VAR))
                .parametersFinal(flag(options, PARAMETERS_FINAL))
                .methodsFinal(flag(options, METHODS_FINAL))
                .classesFinal(flag(options, CLASSES_FINAL))
                .docTags(flag(options, DOC_TAGS))
                .raw(options)
                .build();
    }

    // The comma-separated custom nullable annotations, empty segments dropped so a trailing comma is harmless.
    @VisibleForTesting
    Set<String> nullableAnnotations(final Map<String, String> options) {
        final var raw = options.get(NULLABLE_ANNOTATIONS);
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        return stream(raw.split(",")).filter(segment -> !segment.isEmpty()).collect(toUnmodifiableSet());
    }

    @VisibleForTesting
    boolean flag(final Map<String, String> options, final String key) {
        return "true".equalsIgnoreCase(options.getOrDefault(key, "false"));
    }
}
