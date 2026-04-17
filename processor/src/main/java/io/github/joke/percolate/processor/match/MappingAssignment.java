package io.github.joke.percolate.processor.match;

import io.github.joke.percolate.MapOptKey;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

/**
 * An immutable record of one source-path → target-name decision produced by {@code MatchMappingsStage}.
 *
 * <p>Construct via {@link #of} — the factory normalises empty {@code using} strings to {@code null}
 * and defensively copies the collections.
 * The class carries no type information; type resolution is {@code BuildValueGraphStage}'s job.
 */
@Getter
@ToString
@EqualsAndHashCode
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class MappingAssignment {

    private final List<String> sourcePath;
    private final String targetName;
    private final Map<MapOptKey, String> options;

    @Nullable
    private final String using;

    private final AssignmentOrigin origin;

    /**
     * Creates a {@code MappingAssignment}, normalising an empty {@code using} string to {@code null}
     * and defensively copying the source-path list and options map.
     */
    public static MappingAssignment of(
            final List<String> sourcePath,
            final String targetName,
            final Map<MapOptKey, String> options,
            final @Nullable String using,
            final AssignmentOrigin origin) {
        return new MappingAssignment(
                List.copyOf(sourcePath),
                targetName,
                Map.copyOf(options),
                (using == null || using.isEmpty()) ? null : using,
                origin);
    }
}
