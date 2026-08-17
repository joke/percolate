package io.github.joke.percolate.processor;

import io.github.joke.percolate.spi.SwitchStyle;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

// Deliberately a data class since change tighten-testability-conventions moved every parsing decision to
// ProcessorOptionsReader (design D2) — the behaviour PMD looks for here is exactly what was extracted.
@SuppressWarnings("PMD.DataClass")
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
    public static final String SWITCH_STYLE = "percolate.switch.style";

    boolean debugGraphs;
    Set<String> customNullableAnnotations;
    boolean localsFinal;
    boolean localsVar;
    boolean parametersFinal;
    boolean methodsFinal;
    boolean classesFinal;
    boolean docTags;
    Optional<String> timeZone;
    SwitchStyle switchStyle;

    // The raw -A option map, carried verbatim so the per-mapper ResolveCtx can answer ResolveCtx.option(key) for
    // any declared key without a per-feature field (change add-builder-assembly). Strategy-consumed options are
    // read from here and parsed by the strategy that owns their meaning; the typed fields above serve the
    // engine-internal consumers.
    Map<String, String> raw;

    @Builder
    @SuppressWarnings("PMD.ExcessiveParameterList")
    public ProcessorOptions(
            final boolean debugGraphs,
            final Set<String> customNullableAnnotations,
            final boolean localsFinal,
            final boolean localsVar,
            final boolean parametersFinal,
            final boolean methodsFinal,
            final boolean classesFinal,
            final boolean docTags,
            final Optional<String> timeZone,
            final SwitchStyle switchStyle,
            final Map<String, String> raw) {
        this.debugGraphs = debugGraphs;
        this.customNullableAnnotations = Set.copyOf(customNullableAnnotations);
        this.localsFinal = localsFinal;
        this.localsVar = localsVar;
        this.parametersFinal = parametersFinal;
        this.methodsFinal = methodsFinal;
        this.classesFinal = classesFinal;
        this.docTags = docTags;
        this.timeZone = timeZone;
        this.switchStyle = switchStyle;
        this.raw = Map.copyOf(raw);
    }
}
