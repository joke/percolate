package io.github.joke.percolate.processor.spi;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import lombok.Value;

@Value
public class ResolutionContext {
    Types types;
    Elements elements;
    TypeElement mapperType;
    ExecutableElement currentMethod;
}
