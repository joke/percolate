package io.github.joke.percolate.processor.test.fixtures;

import io.github.joke.percolate.Mapper;
import org.jspecify.annotations.NullMarked;

@NullMarked
@Mapper
public interface PersonMapper {
    Human map(Person person);
}
