package io.github.joke.percolate.docs.extending;

import io.github.joke.percolate.spi.DirectiveInput;
import io.github.joke.percolate.spi.DirectiveReader;
import io.github.joke.percolate.spi.DirectiveSink;
import io.github.joke.percolate.spi.Subjects;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import org.jspecify.annotations.Nullable;

// tag::reader[]
// A reader's whole job: translate the annotation it owns into DirectiveSink calls. It uses the same
// bind/input vocabulary @Map's own reader does, so LiteralValue below needs no engine support beyond what
// the built-in ConstantValue already gets.
public final class LiteralDirectiveReader implements DirectiveReader {

    @Override
    public void read(final ExecutableElement method, final DirectiveSink sink) {
        final var mirror = mirrorOf(method);
        if (mirror == null) {
            return;
        }
        final var literal = method.getAnnotation(Literal.class);
        final var targetPath = List.of(literal.target().split("\\."));
        final var subject = Subjects.of(method, mirror, null);
        sink.bind(targetPath, List.of(), subject);
        sink.input(targetPath, DirectiveInput.scalar("literal", literal.value(), subject));
    }

    static @Nullable AnnotationMirror mirrorOf(final ExecutableElement method) {
        return method.getAnnotationMirrors().stream()
                .filter(mirror -> ((TypeElement) mirror.getAnnotationType().asElement())
                        .getQualifiedName()
                        .contentEquals(Literal.class.getCanonicalName()))
                .findFirst()
                .orElse(null);
    }
}
// end::reader[]
