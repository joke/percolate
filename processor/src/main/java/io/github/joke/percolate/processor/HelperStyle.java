package io.github.joke.percolate.processor;

import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.Modifier;
import lombok.Value;

import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * The modifiers the generate stage puts on every strategy-requested class member, read from
 * {@code percolate.helpers.visibility} and {@code percolate.helpers.static} (design D3 of change
 * {@code add-setter-assembly}). The two options always travel together — a member is emitted with both or
 * neither — so they are one value rather than two loose fields on {@link ProcessorOptions}.
 *
 * <p>Style only: neither option changes which members are emitted, their dedup identity, their order, or their
 * allocated names.
 */
@Value
public class HelperStyle {

    MemberVisibility visibility;

    boolean membersStatic;

    /** The configured visibility, plus {@code static} when {@code percolate.helpers.static} is on. */
    public Modifier[] memberModifiers() {
        final var modifiers = new ArrayList<>(visibility.modifiers());
        if (membersStatic) {
            modifiers.add(STATIC);
        }
        return modifiers.toArray(new Modifier[0]);
    }

    /**
     * A field carries the member modifiers plus an unconditional {@code final}: a mutable shared field would be
     * a behaviour change rather than a style one, so the options do not reach it.
     */
    public Modifier[] fieldModifiers() {
        final var modifiers = new ArrayList<>(List.of(memberModifiers()));
        modifiers.add(FINAL);
        return modifiers.toArray(new Modifier[0]);
    }
}
