package io.github.joke.percolate.spi.builtins;

import com.google.auto.service.AutoService;
import com.groupcdg.pitest.annotations.CoverageIgnore;
import io.github.joke.percolate.Map;
import io.github.joke.percolate.spi.DirectiveInput;
import io.github.joke.percolate.spi.DirectiveReader;
import io.github.joke.percolate.spi.DirectiveSink;
import io.github.joke.percolate.spi.Subject;
import io.github.joke.percolate.spi.Subjects;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;

import static io.github.joke.percolate.spi.DirectiveInput.scalar;
import static java.util.Objects.requireNonNull;

// Reads a method's @Map/@MapList declarations into DirectiveSink calls (design D4/D7 of change decouple-engine-
// from-strategy-semantics): target is always written, while source, constant, defaultValue, format and zone
// become an open input only when actually written — AnnotationMirror.getElementValues() decides presence, so an
// empty string is present, not absent.
//
// @Map's own shape rules — source XOR constant, and defaultValue requires a source — are enforced here, the
// annotation's own reader, rather than by the core: a violation declines to bind (so the path is never
// assembled as a real binding) and rejects the declaration with a positioned reason the core reports verbatim,
// without ever learning @Map's vocabulary.
//
// A rejection, not a io.github.joke.percolate.spi.Constraint: a constraint is only ever heard when some
// strategy offers a candidate to refuse, and a malformed declaration typically leaves nothing to offer — the
// violation would then vanish behind a generic "no plan" line, or behind an unrelated refusal at a shallower
// miss.
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

    @VisibleForTesting
    void readEntry(final ExecutableElement method, final AnnotationMirror mirror, final DirectiveSink sink) {
        final var written = AnnotationEntries.writtenMembers(mirror);
        final var targetValue = requireNonNull(written.get(TARGET));
        final var targetPath = splitDotted(targetValue.getValue().toString());
        final var targetSubject = Subjects.of(method, mirror, targetValue);

        if (declinesShape(method, mirror, written, sink)) {
            return;
        }

        final var sourcePath = written.containsKey(SOURCE)
                ? splitDotted(written.get(SOURCE).getValue().toString())
                : List.<String>of();
        sink.bind(targetPath, sourcePath, targetSubject);
        OPTIONAL_MEMBERS.stream()
                .filter(key -> !SOURCE.equals(key) && written.containsKey(key))
                .forEach(key -> sink.input(targetPath, toInput(method, mirror, key, requireNonNull(written.get(key)))));
    }

    // Enforces @Map's own shape rules, refusing the target path outright when violated.
    @VisibleForTesting
    boolean declinesShape(
            final ExecutableElement method,
            final AnnotationMirror mirror,
            final java.util.Map<String, AnnotationValue> written,
            final DirectiveSink sink) {
        final var shape = new Shape(method, mirror, written, sink);
        return shape.declinesBothSourceAndConstant()
                || shape.declinesNeitherSourceNorConstant()
                || shape.declinesDefaultValueWithoutSource();
    }

    // One @Map entry's shape-rule context, so each rule is a single early-return branch.
    @RequiredArgsConstructor
    private static final class Shape {
        private final ExecutableElement method;
        private final AnnotationMirror mirror;
        private final java.util.Map<String, AnnotationValue> written;
        private final DirectiveSink sink;

        @VisibleForTesting
        boolean declinesBothSourceAndConstant() {
            if (!(hasSource() && hasConstant())) {
                return false;
            }
            refuse(
                    Subjects.of(method, mirror, written.get(CONSTANT)),
                    "@Map declares both 'source' and 'constant'; they are mutually exclusive");
            return true;
        }

        @VisibleForTesting
        boolean declinesNeitherSourceNorConstant() {
            if (hasSource() || hasConstant()) {
                return false;
            }
            refuse(
                    Subjects.of(method, mirror, requireNonNull(written.get(TARGET))),
                    "@Map must declare a 'source' or a 'constant'");
            return true;
        }

        @VisibleForTesting
        boolean declinesDefaultValueWithoutSource() {
            if (!written.containsKey(DEFAULT_VALUE) || hasSource()) {
                return false;
            }
            refuse(Subjects.of(method, mirror, written.get(DEFAULT_VALUE)), "@Map 'defaultValue' requires a 'source'");
            return true;
        }

        @VisibleForTesting
        boolean hasSource() {
            return written.containsKey(SOURCE);
        }

        @VisibleForTesting
        boolean hasConstant() {
            return written.containsKey(CONSTANT);
        }

        // Rejects the declaration outright, so the reason is reported whether or not anything demands the path.
        @VisibleForTesting
        void refuse(final Subject subject, final String message) {
            sink.reject(subject, message);
        }
    }

    @VisibleForTesting
    DirectiveInput toInput(
            final ExecutableElement method,
            final AnnotationMirror mirror,
            final String key,
            final AnnotationValue value) {
        return scalar(key, value.getValue().toString(), Subjects.of(method, mirror, value));
    }

    @VisibleForTesting
    List<String> splitDotted(final String path) {
        if (path.isEmpty()) {
            return List.of();
        }
        return List.of(path.split("\\.", -1));
    }
}
