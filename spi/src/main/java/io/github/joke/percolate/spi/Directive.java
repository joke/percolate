package io.github.joke.percolate.spi;

import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toUnmodifiableList;

/**
 * The {@code @Map} configuration in effect at a {@link Demand}, exposed to strategies without raw compiler
 * internals (design D3 of change {@code decouple-engine-from-strategy-semantics}). {@link #sourcePath()} is
 * structural — the engine walks it. Everything else is an open, keyed bag of {@link DirectiveInput}s: a strategy
 * reads its own member by key (e.g. {@code "constant"}, {@code "format"}, {@code "enum"}) rather than through a
 * closed set of typed accessors, so a new {@code @Map} member — or a third-party annotation's own vocabulary —
 * touches no core type.
 *
 * <p>An input is reported present only when its member was actually written; an empty string is a present value,
 * never absent. {@code ConstantValue} reads {@code "constant"}, {@code NullnessCrossing} reads {@code
 * "defaultValue"}; the temporal strategies read {@code "format"} and {@code "zone"}; {@code EnumConversion} reads
 * every repeated {@code "enum"} entry via {@link #inputs(String)}.
 */
public interface Directive {

    /** The {@code @Map} source path split into segments, e.g. {@code ["person", "address", "street"]}; empty for a constant. */
    List<String> sourcePath();

    /** Every input this directive declares, in declaration order. */
    List<DirectiveInput> inputs();

    /** The sole input declared under {@code key}, or empty when none was written. */
    default Optional<DirectiveInput> input(final String key) {
        return inputs().stream().filter(i -> i.getKey().equals(key)).findFirst();
    }

    /** Every input declared under {@code key}, in declaration order — for a repeatable, structured member (e.g. {@code "enum"}). */
    default List<DirectiveInput> inputs(final String key) {
        return inputs().stream().filter(i -> i.getKey().equals(key)).collect(toUnmodifiableList());
    }

    /** By-key convenience: the scalar value declared under {@code key}, or empty when absent. */
    default Optional<String> value(final String key) {
        return input(key).flatMap(DirectiveInput::getValue);
    }
}
