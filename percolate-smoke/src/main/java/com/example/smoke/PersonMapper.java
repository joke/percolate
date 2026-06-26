package com.example.smoke;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;

/**
 * A minimal consumer mapper. The String copy exercises a direct-assign + getter-path builtin and the
 * {@code int -> Integer} field exercises a conversion builtin, so a successful build proves the starter
 * put the engine AND the builtins on the annotationProcessor classpath.
 */
@Mapper
public interface PersonMapper {

    @Map(target = "firstName", source = "person.firstName")
    @Map(target = "age", source = "person.age")
    Human map(Person person);
}
