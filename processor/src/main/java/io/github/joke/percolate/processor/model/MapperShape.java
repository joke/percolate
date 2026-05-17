package io.github.joke.percolate.processor.model;

import java.util.List;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import lombok.Value;

@Value
public class MapperShape {
    TypeElement type;
    List<ExecutableElement> abstractMethods;
}
