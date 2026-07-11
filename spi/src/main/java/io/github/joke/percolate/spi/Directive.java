package io.github.joke.percolate.spi;

import java.util.List;
import java.util.Optional;

/**
 * The {@code @Map} configuration in effect at a {@link Demand}, exposed to strategies without raw compiler
 * internals. A strategy reads its per-binding configuration here — the source path it descends, and the
 * author-declared {@code constant} / {@code defaultValue} / {@code format} / {@code zone} attributes — rather than
 * inspecting an {@code AnnotationMirror}.
 *
 * <p>{@link #constant()}, {@link #defaultValue()}, {@link #format()}, and {@link #zone()} are reported
 * <strong>present</strong> only when the member is not the {@code Map.UNSET} sentinel; an empty string is a present
 * value, never absent. {@code ConstantValue} reads {@link #constant()} and {@code NullnessCrossing} reads
 * {@link #defaultValue()} through this surface; the temporal strategies read {@link #format()} and {@link #zone()}.
 */
public interface Directive {

    /** The {@code @Map} source path split into segments, e.g. {@code ["person", "address", "street"]}; empty for a constant. */
    List<String> sourcePath();

    /** The {@code @Map} {@code constant} literal (present — including the empty string — iff declared), else empty. */
    Optional<String> constant();

    /** The {@code @Map} {@code defaultValue} (present — including the empty string — iff declared), else empty. */
    Optional<String> defaultValue();

    /** The {@code @Map} {@code format} option (present — including the empty string — iff declared), else empty. */
    Optional<String> format();

    /** The {@code @Map} {@code zone} option (present — including the empty string — iff declared), else empty. */
    Optional<String> zone();
}
