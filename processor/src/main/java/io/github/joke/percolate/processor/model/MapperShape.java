package io.github.joke.percolate.processor.model;

import lombok.Value;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.List;

@Value
public class MapperShape {
    TypeElement type;
    List<ExecutableElement> abstractMethods;
}
