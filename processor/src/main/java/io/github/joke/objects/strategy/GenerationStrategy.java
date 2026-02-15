package io.github.joke.objects.strategy;

import javax.lang.model.element.TypeElement;

public interface GenerationStrategy {
    void generate(TypeElement source, ClassModel model);
}
