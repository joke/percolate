package io.github.joke.percolate.processor;

import io.github.joke.percolate.spi.SwitchStyle;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;

// Reads the raw -A option map into a ProcessorOptions. Split out of that value type by change tighten-
// testability-conventions (design D2): the parsing decisions — a missing flag defaulting to false, an
// unparseable switch.style degrading to AUTO rather than failing the round, an empty nullable.annotations
// meaning "none declared" rather than one empty name — are the part worth testing, and as statics on the value
// type they could not be intercepted. ProcessorOptions is left as plain data.
@NoArgsConstructor(onConstructor_ = @Inject)
public class ProcessorOptionsReader {

    public ProcessorOptions from(final Map<String, String> options) {
        return ProcessorOptions.builder()
                .debugGraphs(flag(options, ProcessorOptions.DEBUG_GRAPHS))
                .customNullableAnnotations(nullableAnnotations(options))
                .localsFinal(flag(options, ProcessorOptions.LOCALS_FINAL))
                .localsVar(flag(options, ProcessorOptions.LOCALS_VAR))
                .parametersFinal(flag(options, ProcessorOptions.PARAMETERS_FINAL))
                .methodsFinal(flag(options, ProcessorOptions.METHODS_FINAL))
                .classesFinal(flag(options, ProcessorOptions.CLASSES_FINAL))
                .docTags(flag(options, ProcessorOptions.DOC_TAGS))
                .timeZone(Optional.ofNullable(options.get(ProcessorOptions.TIME_ZONE)))
                .switchStyle(parseSwitchStyle(options))
                .build();
    }

    // The comma-separated custom nullable annotations, empty segments dropped so a trailing comma is harmless.
    Set<String> nullableAnnotations(final Map<String, String> options) {
        final var raw = options.get(ProcessorOptions.NULLABLE_ANNOTATIONS);
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .filter(segment -> !segment.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    boolean flag(final Map<String, String> options, final String key) {
        return "true".equalsIgnoreCase(options.getOrDefault(key, "false"));
    }

    // An unrecognised or absent switch.style degrades to AUTO — never fails the round.
    SwitchStyle parseSwitchStyle(final Map<String, String> options) {
        final var raw = options.get(ProcessorOptions.SWITCH_STYLE);
        if (raw == null) {
            return SwitchStyle.AUTO;
        }
        try {
            return SwitchStyle.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return SwitchStyle.AUTO;
        }
    }
}
