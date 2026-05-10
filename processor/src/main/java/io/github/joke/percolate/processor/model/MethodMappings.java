package io.github.joke.percolate.processor.model;

import lombok.Value;

import javax.lang.model.element.ExecutableElement;
import java.util.List;

@Value
public class MethodMappings {
    ExecutableElement method;
    List<MappingDirective> directives;
}
