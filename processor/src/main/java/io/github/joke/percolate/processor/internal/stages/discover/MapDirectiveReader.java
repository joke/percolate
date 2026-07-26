package io.github.joke.percolate.processor.internal.stages.discover;

import static java.util.stream.Collectors.toUnmodifiableList;

import com.groupcdg.pitest.annotations.CoverageIgnore;
import io.github.joke.percolate.Map;
import io.github.joke.percolate.MapList;
import io.github.joke.percolate.processor.model.MappingDirective;
import io.github.joke.percolate.spi.DirectiveInput;
import io.github.joke.percolate.spi.Subjects;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Objects;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import lombok.RequiredArgsConstructor;

/**
 * Reads a method's {@code @Map}/{@code @MapList} declarations into {@link MappingDirective}s (design D4 of change
 * {@code decouple-engine-from-strategy-semantics}): {@code target} is always written, while {@code source},
 * {@code constant}, {@code defaultValue}, {@code format} and {@code zone} become an open {@link DirectiveInput} bag
 * entry only when actually written — {@code AnnotationMirror.getElementValues()} decides presence, so an empty
 * string is present, not absent.
 */
@CoverageIgnore
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class MapDirectiveReader {

    private static final String TARGET = "target";
    private static final List<String> OPTIONAL_MEMBERS =
            List.of("source", "constant", "defaultValue", "format", "zone");

    private final AnnotationEntryReader entryReader;

    List<MappingDirective> extractDirectives(final ExecutableElement method) {
        return entryReader.entriesOf(Map.class, method.getAnnotationMirrors()).stream()
                .map(mirror -> toDirective(method, mirror))
                .collect(toUnmodifiableList());
    }

    MappingDirective toDirective(final ExecutableElement method, final AnnotationMirror mirror) {
        final var written = AnnotationEntryReader.writtenMembers(mirror);
        final AnnotationValue targetValue = Objects.requireNonNull(written.get(TARGET));
        final var inputs = OPTIONAL_MEMBERS.stream()
                .filter(written::containsKey)
                .map(key -> toInput(method, mirror, key, Objects.requireNonNull(written.get(key))))
                .collect(toUnmodifiableList());
        return new MappingDirective(
                targetValue.getValue().toString(), Subjects.of(method, mirror, targetValue), inputs);
    }

    static DirectiveInput toInput(
            final ExecutableElement method,
            final AnnotationMirror mirror,
            final String key,
            final AnnotationValue value) {
        return DirectiveInput.scalar(key, value.getValue().toString(), Subjects.of(method, mirror, value));
    }

    /** Unused directly — {@link MapList} is unwrapped generically by {@link AnnotationEntryReader}; kept for the javadoc link. */
    static Class<MapList> containerType() {
        return MapList.class;
    }
}
