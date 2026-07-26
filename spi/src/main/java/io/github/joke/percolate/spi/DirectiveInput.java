package io.github.joke.percolate.spi;

import java.util.Map;
import java.util.Optional;
import lombok.Value;

/**
 * One opaque entry of a {@link Directive}'s open bag (design D3 of change
 * {@code decouple-engine-from-strategy-semantics}): {@code key} names the member ({@code "constant"}, {@code
 * "format"}, {@code "enum"}, …); a <b>scalar</b> input (e.g. {@code @Map(format = "…")}) carries its literal in
 * {@link #getValue()}; a <b>structured</b>, repeatable input (e.g. one {@code @MapEnum} entry) carries its named parts
 * through {@link #member(String)} instead. {@link #getSubject()} is the opaque positioning handle a reader captured
 * while parsing the annotation member — a strategy never builds one, only hands it back, e.g. inside a refusal.
 */
@Value
public class DirectiveInput {

    String key;
    Optional<String> value;
    Map<String, String> members;
    Subject subject;

    /** A structured input's named part (e.g. {@code "source"}/{@code "target"} for one {@code @MapEnum} entry). */
    public Optional<String> member(final String name) {
        return Optional.ofNullable(members.get(name));
    }

    /** A scalar input: {@code value} is its literal, {@link #member(String)} always empty. */
    public static DirectiveInput scalar(final String key, final String value, final Subject subject) {
        return new DirectiveInput(key, Optional.of(value), Map.of(), subject);
    }

    /** A structured, repeatable input: {@link #getValue()} always empty, its named parts reachable via {@link #member(String)}. */
    public static DirectiveInput structured(
            final String key, final Map<String, String> members, final Subject subject) {
        return new DirectiveInput(key, Optional.empty(), Map.copyOf(members), subject);
    }
}
