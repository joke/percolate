package io.github.joke.percolate.processor.model;

import io.github.joke.percolate.spi.Subject;
import lombok.Value;

/**
 * One discovered {@code @MapEnum} declaration. Unlike {@link MappingDirective}, both members are mandatory (the
 * annotation declares no default), so there is no presence decision — the reader hands this type straight to
 * callers, carrying the {@link Subject} each member's reader built (design D14 of change
 * {@code decouple-engine-from-strategy-semantics}) so a diagnostic can underline the exact written literal without
 * touching a raw {@code AnnotationMirror}/{@code AnnotationValue} again.
 */
@Value
public class EnumOverrideDirective {
    String source;
    String target;
    Subject sourceSubject;
    Subject targetSubject;
}
