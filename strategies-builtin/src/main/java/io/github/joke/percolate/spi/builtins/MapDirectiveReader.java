package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.groupcdg.pitest.annotations.CoverageIgnore;
import io.github.joke.percolate.Map;
import io.github.joke.percolate.spi.DirectiveInput;
import io.github.joke.percolate.spi.DirectiveReader;
import io.github.joke.percolate.spi.DirectiveSink;
import io.github.joke.percolate.spi.Offer;
import io.github.joke.percolate.spi.Subject;
import io.github.joke.percolate.spi.Subjects;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

/**
 * Reads a method's {@code @Map}/{@code @MapList} declarations into {@link DirectiveSink} calls (design D4/D7 of
 * change {@code decouple-engine-from-strategy-semantics}): {@code target} is always written, while {@code source},
 * {@code constant}, {@code defaultValue}, {@code format} and {@code zone} become an open input only when actually
 * written — {@code AnnotationMirror.getElementValues()} decides presence, so an empty string is present, not absent.
 *
 * <p>{@code @Map}'s own shape rules — <b>source XOR constant</b>, and <b>{@code defaultValue} requires a
 * source</b> — are enforced here, the annotation's own reader, rather than by the core: a violation declines to
 * {@code bind} (so the path is never assembled as a real binding) and instead {@code constrain}s the target path
 * with an always-refusing {@link io.github.joke.percolate.spi.Constraint}, so the contradiction still surfaces as a
 * positioned compile error if anything ever demands that path, without the core ever learning {@code @Map}'s
 * vocabulary.
 */
@CoverageIgnore
@AutoService(DirectiveReader.class)
@NoArgsConstructor
public final class MapDirectiveReader implements DirectiveReader {

    private static final String TARGET = "target";
    private static final String SOURCE = "source";
    private static final String CONSTANT = "constant";
    private static final String DEFAULT_VALUE = "defaultValue";
    private static final List<String> OPTIONAL_MEMBERS = List.of(SOURCE, CONSTANT, DEFAULT_VALUE, "format", "zone");

    @Override
    public void read(final ExecutableElement method, final DirectiveSink sink) {
        AnnotationEntries.entriesOf(Map.class, method).forEach(mirror -> readEntry(method, mirror, sink));
    }

    void readEntry(final ExecutableElement method, final AnnotationMirror mirror, final DirectiveSink sink) {
        final var written = AnnotationEntries.writtenMembers(mirror);
        final var targetValue = Objects.requireNonNull(written.get(TARGET));
        final var targetPath = splitDotted(targetValue.getValue().toString());
        final var targetSubject = Subjects.of(method, mirror, targetValue);

        if (declinesShape(method, mirror, written, targetPath, sink)) {
            return;
        }

        final var sourcePath = written.containsKey(SOURCE)
                ? splitDotted(written.get(SOURCE).getValue().toString())
                : List.<String>of();
        sink.bind(targetPath, sourcePath, targetSubject);
        OPTIONAL_MEMBERS.stream()
                .filter(key -> !SOURCE.equals(key) && written.containsKey(key))
                .forEach(key ->
                        sink.input(targetPath, toInput(method, mirror, key, Objects.requireNonNull(written.get(key)))));
    }

    /** Enforces {@code @Map}'s own shape rules, refusing the target path outright when violated. */
    boolean declinesShape(
            final ExecutableElement method,
            final AnnotationMirror mirror,
            final java.util.Map<String, AnnotationValue> written,
            final List<String> targetPath,
            final DirectiveSink sink) {
        final var shape = new Shape(method, mirror, written, targetPath, sink);
        return shape.declinesBothSourceAndConstant()
                || shape.declinesNeitherSourceNorConstant()
                || shape.declinesDefaultValueWithoutSource();
    }

    /** One {@code @Map} entry's shape-rule context, so each rule is a single early-return branch. */
    @RequiredArgsConstructor
    private static final class Shape {
        private final ExecutableElement method;
        private final AnnotationMirror mirror;
        private final java.util.Map<String, AnnotationValue> written;
        private final List<String> targetPath;
        private final DirectiveSink sink;

        boolean declinesBothSourceAndConstant() {
            if (!(hasSource() && hasConstant())) {
                return false;
            }
            refuse(
                    Subjects.of(method, mirror, written.get(CONSTANT)),
                    "@Map declares both 'source' and 'constant'; they are mutually exclusive");
            return true;
        }

        boolean declinesNeitherSourceNorConstant() {
            if (hasSource() || hasConstant()) {
                return false;
            }
            refuse(
                    Subjects.of(method, mirror, Objects.requireNonNull(written.get(TARGET))),
                    "@Map must declare a 'source' or a 'constant'");
            return true;
        }

        boolean declinesDefaultValueWithoutSource() {
            if (!written.containsKey(DEFAULT_VALUE) || hasSource()) {
                return false;
            }
            refuse(Subjects.of(method, mirror, written.get(DEFAULT_VALUE)), "@Map 'defaultValue' requires a 'source'");
            return true;
        }

        boolean hasSource() {
            return written.containsKey(SOURCE);
        }

        boolean hasConstant() {
            return written.containsKey(CONSTANT);
        }

        /** Attaches an always-refusing {@link io.github.joke.percolate.spi.Constraint} at {@code targetPath}. */
        void refuse(final Subject subject, final String message) {
            sink.constrain(targetPath, (candidate, boundPorts) -> Optional.of(new Offer.Refusal(subject, message)));
        }
    }

    static DirectiveInput toInput(
            final ExecutableElement method,
            final AnnotationMirror mirror,
            final String key,
            final AnnotationValue value) {
        return DirectiveInput.scalar(key, value.getValue().toString(), Subjects.of(method, mirror, value));
    }

    static List<String> splitDotted(final String path) {
        if (path.isEmpty()) {
            return List.of();
        }
        return List.of(path.split("\\.", -1));
    }
}
