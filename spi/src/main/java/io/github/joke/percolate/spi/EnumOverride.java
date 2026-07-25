package io.github.joke.percolate.spi;

import lombok.Value;

/**
 * One {@code @MapEnum(source = "…", target = "…")} declaration: the source enum constant's simple name and the
 * target enum constant it maps to. Carried, in declaration order, in the {@link Directive}'s
 * {@link Directive#enumOverrides()} table exactly as {@code @Map}'s other members travel — never stamped on a
 * {@code Value}.
 */
@Value
public class EnumOverride {
    String source;
    String target;
}
