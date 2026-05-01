package io.github.joke.percolate.processor.model;

import java.util.List;
import javax.lang.model.element.ExecutableElement;
import lombok.Value;

@Value
public class MethodMappings {
    ExecutableElement method;
    List<MappingDirective> directives;
}
