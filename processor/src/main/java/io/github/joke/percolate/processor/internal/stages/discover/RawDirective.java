package io.github.joke.percolate.processor.internal.stages.discover;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import lombok.Value;

/**
 * One {@code @Map} directive read off an {@link AnnotationMirror} as <em>plain data</em>: each member's written string
 * (still at the {@code Map.UNSET} sentinel when absent — the sentinel test belongs to {@link MappingDirectiveBuilder},
 * not the reader), paired with the opaque {@link AnnotationValue}/{@link AnnotationMirror} tokens the builder forwards
 * untouched so a diagnostic can later underline the exact written literal (design D6). The tokens are carried, never
 * interrogated, downstream.
 */
@Value
class RawDirective {
    String target;
    String source;
    String constant;
    String defaultValue;
    String format;
    String zone;

    AnnotationMirror mirror;
    AnnotationValue targetValue;
    AnnotationValue sourceValue;
    AnnotationValue constantValue;
    AnnotationValue defaultValueValue;
    AnnotationValue formatValue;
    AnnotationValue zoneValue;
}
