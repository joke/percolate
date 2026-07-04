package io.github.joke.percolate.spi.types;

import io.github.joke.percolate.spi.Nullability;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A field's model signature: name, declaring type, type, <b>resolved</b> nullness, and the member flags
 * production filters on (visibility, staticness). The {@link Origin} is diagnostic addressing, not identity —
 * it is excluded from equality.
 */
@Value
public class FieldSig {
    String name;
    String declaredIn;
    TypeRef type;
    Nullability nullness;
    Set<MemberFlag> flags;

    @EqualsAndHashCode.Exclude
    Origin origin;

    /** Whether this field carries {@code flag}. */
    public boolean has(final MemberFlag flag) {
        return flags.contains(flag);
    }
}
