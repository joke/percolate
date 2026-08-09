package io.github.joke.percolate.processor.violators;

import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;

/**
 * Violates: no engine class reads a raw annotation off an Element. The receiver is declared as
 * {@code Element} on purpose - the rule tests {@code isAssignableTo(Element)} on the call target's owner,
 * so an {@code AnnotatedConstruct} receiver would not trip it.
 */
public class EngineReadsRawAnnotation {
    public List<? extends AnnotationMirror> read(final Element element) {
        return element.getAnnotationMirrors();
    }
}
