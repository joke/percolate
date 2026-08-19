package io.github.joke.percolate.processor;

import java.util.Set;
import javax.lang.model.element.Modifier;
import org.jspecify.annotations.Nullable;

import static java.util.Locale.ROOT;

/**
 * The access modifier the generate stage puts on every strategy-requested class member, read from
 * {@code percolate.helpers.visibility} (design D3 of change {@code add-setter-assembly}). Each constant's
 * lower-case name is the option token it answers to. It is deliberately <b>not</b> the SPI's
 * {@link io.github.joke.percolate.spi.Visibility}, which names scope-input reachability and has nothing to do
 * with generated Java modifiers.
 */
public enum MemberVisibility {

    /** The default: the member is reachable only from the generated mapper. */
    PRIVATE(Modifier.PRIVATE),

    /** No access modifier at all — package-private. */
    PACKAGE(null),

    /** Reachable from a subclass of the generated mapper. */
    PROTECTED(Modifier.PROTECTED),

    /** Reachable from anywhere. */
    PUBLIC(Modifier.PUBLIC);

    // A single enum constant rather than a Set, so Error Prone's ImmutableEnumChecker can see the field is
    // immutable; Set.of(...) is immutable in fact but not by its declared type.
    private final @Nullable Modifier modifier;

    MemberVisibility(final @Nullable Modifier modifier) {
        this.modifier = modifier;
    }

    /** The option token this constant answers to. */
    public String token() {
        return name().toLowerCase(ROOT);
    }

    /** The modifiers this visibility contributes to a generated member. Empty for package-private. */
    public Set<Modifier> modifiers() {
        return modifier == null ? Set.of() : Set.of(modifier);
    }
}
