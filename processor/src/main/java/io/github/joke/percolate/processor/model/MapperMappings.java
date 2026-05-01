package io.github.joke.percolate.processor.model;

import java.util.List;
import javax.lang.model.element.TypeElement;
import lombok.Value;

@Value
public class MapperMappings {
    TypeElement type;
    List<MethodMappings> methods;
}
