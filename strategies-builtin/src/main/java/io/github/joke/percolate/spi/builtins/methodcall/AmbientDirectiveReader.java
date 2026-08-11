package io.github.joke.percolate.spi.builtins.methodcall;

import com.google.auto.service.AutoService;
import com.groupcdg.pitest.annotations.CoverageIgnore;
import io.github.joke.percolate.Ambient;
import io.github.joke.percolate.spi.DirectiveReader;
import io.github.joke.percolate.spi.DirectiveSink;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.VisibleForTesting;

import static io.github.joke.percolate.spi.Visibility.INHERITED;

// Publishes each @Ambient-annotated parameter as a named, Visibility.INHERITED scope input (design D5/D7 of
// change decouple-engine-from-strategy-semantics): an explicit Ambient.value() overrides the published name,
// else it is the parameter's own simple name. Every other parameter defaults to Visibility.LOCAL under its own
// simple name without this reader saying anything at all — that default is the engine's own, not a fact this
// reader publishes.
@CoverageIgnore
@AutoService(DirectiveReader.class)
@NoArgsConstructor
public final class AmbientDirectiveReader implements DirectiveReader {

    @Override
    public void read(final ExecutableElement method, final DirectiveSink sink) {
        method.getParameters().forEach(param -> publishAmbient(param, sink));
    }

    // Publishes param as an INHERITED scope input when it carries @Ambient, and says nothing otherwise.
    @VisibleForTesting
    void publishAmbient(final VariableElement param, final DirectiveSink sink) {
        final var ambient = param.getAnnotation(Ambient.class);
        if (ambient == null) {
            return;
        }
        final var name = ambient.value().isEmpty() ? param.getSimpleName().toString() : ambient.value();
        sink.scopeInput(param, name, INHERITED);
    }
}
