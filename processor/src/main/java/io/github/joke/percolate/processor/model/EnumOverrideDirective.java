package io.github.joke.percolate.processor.model;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import lombok.Value;

/**
 * One discovered {@code @MapEnum} declaration. Unlike {@link MappingDirective}, both members are mandatory (the
 * annotation declares no default), so there is no {@code Map.UNSET}-style presence decision — the reader hands
 * this type straight to callers, carrying the opaque {@link AnnotationMirror}/{@link AnnotationValue} tokens a
 * diagnostic underlines to point at the exact written literal.
 */
@Value
public class EnumOverrideDirective {
    String source;
    String target;
    AnnotationMirror mirror;
    AnnotationValue sourceValue;
    AnnotationValue targetValue;
}
