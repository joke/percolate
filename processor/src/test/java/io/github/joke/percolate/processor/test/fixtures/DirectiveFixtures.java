package io.github.joke.percolate.processor.test.fixtures;

import io.github.joke.percolate.Map;
import io.github.joke.percolate.Mapper;
import org.jspecify.annotations.NullMarked;

/**
 * A {@code @Mapper} fixture whose methods carry every shape of {@code @Map} directive, so the discovery stage can be
 * unit-tested at its seam by reading the (CLASS-retained) annotation mirrors off this compiled type via
 * {@code TypeUniverse} — no annotation-processing round required.
 */
@NullMarked
@Mapper
public interface DirectiveFixtures {

    @Map(target = "name", source = "in.name", defaultValue = "unknown")
    Human sourceWithDefault(Person in);

    @Map(target = "status", constant = "ACTIVE")
    Human constantOnly(Person in);

    @Map(target = "note", constant = "")
    Human emptyConstant(Person in);

    @Map(target = "first", source = "in.first")
    @Map(target = "second", source = "in.second")
    Human repeated(Person in);

    Human none(Person in);

    // A non-@Map annotation (CLASS/RUNTIME-retained) and a void return, so the discovery stage exercises its
    // "annotation is neither @Map nor @MapList" path and the type assembler its void return-type branch.
    @Deprecated
    void sink(Person in);
}
