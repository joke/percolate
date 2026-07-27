package io.github.joke.percolate.spi.builtins.methodcall;

import com.google.auto.service.AutoService;
import com.groupcdg.pitest.annotations.CoverageIgnore;
import io.github.joke.percolate.Ambient;
import io.github.joke.percolate.spi.DirectiveReader;
import io.github.joke.percolate.spi.DirectiveSink;
import io.github.joke.percolate.spi.Visibility;
import javax.lang.model.element.ExecutableElement;
import lombok.NoArgsConstructor;

/**
 * Publishes each {@code @Ambient}-annotated parameter as a named, {@link Visibility#INHERITED} scope input (design
 * D5/D7 of change {@code decouple-engine-from-strategy-semantics}): an explicit {@link Ambient#value()} overrides
 * the published name, else it is the parameter's own simple name. Every other parameter defaults to
 * {@link Visibility#LOCAL} under its own simple name without this reader saying anything at all — that default is
 * the engine's own, not a fact this reader publishes.
 */
@CoverageIgnore
@AutoService(DirectiveReader.class)
@NoArgsConstructor
public final class AmbientDirectiveReader implements DirectiveReader {

    @Override
    public void read(final ExecutableElement method, final DirectiveSink sink) {
        method.getParameters().forEach(param -> {
            final var ambient = param.getAnnotation(Ambient.class);
            if (ambient == null) {
                return;
            }
            final var name = ambient.value().isEmpty() ? param.getSimpleName().toString() : ambient.value();
            sink.scopeInput(param, name, Visibility.INHERITED);
        });
    }
}
