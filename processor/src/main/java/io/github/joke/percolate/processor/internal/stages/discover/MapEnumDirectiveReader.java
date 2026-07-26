package io.github.joke.percolate.processor.internal.stages.discover;

import static java.util.stream.Collectors.toUnmodifiableList;

import com.groupcdg.pitest.annotations.CoverageIgnore;
import io.github.joke.percolate.MapEnum;
import io.github.joke.percolate.processor.model.EnumOverrideDirective;
import io.github.joke.percolate.spi.Subjects;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Objects;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import lombok.RequiredArgsConstructor;

/**
 * Reads a method's {@code @MapEnum}/{@code @MapEnumList} declarations into {@link EnumOverrideDirective}s, mirroring
 * {@link MapDirectiveReader}. Unlike {@code @Map}, both members are mandatory (no default), so there is no presence
 * decision — every entry's {@code source}/{@code target} are always written.
 */
@CoverageIgnore
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class MapEnumDirectiveReader {

    private static final String SOURCE = "source";
    private static final String TARGET = "target";

    private final AnnotationEntryReader entryReader;

    List<EnumOverrideDirective> extractOverrides(final ExecutableElement method) {
        return entryReader.entriesOf(MapEnum.class, method.getAnnotationMirrors()).stream()
                .map(mirror -> toOverride(method, mirror))
                .collect(toUnmodifiableList());
    }

    EnumOverrideDirective toOverride(final ExecutableElement method, final AnnotationMirror mirror) {
        final var written = AnnotationEntryReader.writtenMembers(mirror);
        final var sourceValue = Objects.requireNonNull(written.get(SOURCE));
        final var targetValue = Objects.requireNonNull(written.get(TARGET));
        return new EnumOverrideDirective(
                sourceValue.getValue().toString(),
                targetValue.getValue().toString(),
                Subjects.of(method, mirror, sourceValue),
                Subjects.of(method, mirror, targetValue));
    }
}
