package io.github.joke.percolate.processor.model;

import lombok.Value;

import javax.lang.model.element.TypeElement;
import java.util.List;

@Value
public class MapperMappings {
    TypeElement type;
    List<MethodMappings> methods;
}
