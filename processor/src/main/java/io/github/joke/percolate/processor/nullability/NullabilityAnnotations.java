package io.github.joke.percolate.processor.nullability;

import java.util.Set;
import lombok.Getter;

@Getter
public final class NullabilityAnnotations {

    private static final String JSPECIFY_NULLABLE = "org.jspecify.annotations.Nullable";
    private static final String JSPECIFY_NULL_MARKED = "org.jspecify.annotations.NullMarked";
    private static final String JSPECIFY_NULL_UNMARKED = "org.jspecify.annotations.NullUnmarked";

    private final Set<String> nullableFqns;
    private final Set<String> markedFqns;
    private final Set<String> unmarkedFqns;

    public NullabilityAnnotations(
            final Set<String> nullableFqns, final Set<String> markedFqns, final Set<String> unmarkedFqns) {
        this.nullableFqns = Set.copyOf(nullableFqns);
        this.markedFqns = Set.copyOf(markedFqns);
        this.unmarkedFqns = Set.copyOf(unmarkedFqns);
    }

    public static NullabilityAnnotations jspecifyDefaults() {
        return new NullabilityAnnotations(
                Set.of(JSPECIFY_NULLABLE), Set.of(JSPECIFY_NULL_MARKED), Set.of(JSPECIFY_NULL_UNMARKED));
    }
}
