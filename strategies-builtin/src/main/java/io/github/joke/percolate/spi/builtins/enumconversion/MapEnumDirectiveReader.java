package io.github.joke.percolate.spi.builtins.enumconversion;

import com.google.auto.service.AutoService;
import com.groupcdg.pitest.annotations.CoverageIgnore;
import io.github.joke.percolate.MapEnum;
import io.github.joke.percolate.spi.DirectiveInput;
import io.github.joke.percolate.spi.DirectiveReader;
import io.github.joke.percolate.spi.DirectiveSink;
import io.github.joke.percolate.spi.Subjects;
import io.github.joke.percolate.spi.builtins.AnnotationEntries;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import lombok.NoArgsConstructor;

/**
 * Reads a method's {@code @MapEnum}/{@code @MapEnumList} declarations into one repeated, structured {@code "enum"}
 * input per entry, attached at the empty root target path — {@code @MapEnum} is method-level, in effect only for a
 * conversion method's own return demand (design D4/D7 of change {@code decouple-engine-from-strategy-semantics}).
 * Unlike {@code @Map}, both members are mandatory (no default), so there is no presence decision.
 */
@CoverageIgnore
@AutoService(DirectiveReader.class)
@NoArgsConstructor
public final class MapEnumDirectiveReader implements DirectiveReader {

    private static final String SOURCE = "source";
    private static final String TARGET = "target";
    private static final String ENUM_KEY = "enum";
    private static final List<String> ROOT_PATH = List.of();

    @Override
    public void read(final ExecutableElement method, final DirectiveSink sink) {
        AnnotationEntries.entriesOf(MapEnum.class, method)
                .forEach(mirror -> sink.input(ROOT_PATH, toInput(method, mirror)));
    }

    DirectiveInput toInput(final ExecutableElement method, final AnnotationMirror mirror) {
        final Map<String, AnnotationValue> written = AnnotationEntries.writtenMembers(mirror);
        final var sourceValue = Objects.requireNonNull(written.get(SOURCE));
        final var targetValue = Objects.requireNonNull(written.get(TARGET));
        return DirectiveInput.structured(
                ENUM_KEY,
                Map.of(
                        SOURCE,
                        sourceValue.getValue().toString(),
                        TARGET,
                        targetValue.getValue().toString()),
                Subjects.of(method, mirror, targetValue));
    }
}
