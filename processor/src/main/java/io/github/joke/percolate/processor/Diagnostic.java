package io.github.joke.percolate.processor;

import javax.lang.model.element.Element;
import javax.tools.Diagnostic.Kind;
import lombok.Value;

@Value
public class Diagnostic {
    Element element;
    String message;
    Kind kind;
}
