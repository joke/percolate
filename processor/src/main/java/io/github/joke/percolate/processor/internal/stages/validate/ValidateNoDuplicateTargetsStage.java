package io.github.joke.percolate.processor.internal.stages.validate;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.Diagnostic;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.internal.stages.Stage;
import io.github.joke.percolate.processor.model.Bind;
import io.github.joke.percolate.processor.model.MethodDirectives;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

// Two bindings at one target path are an error (design D7 of change decouple-engine-from-strategy-semantics)
// regardless of which io.github.joke.percolate.spi.DirectiveReader declared them — a property of the sink, not
// of any annotation.
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class ValidateNoDuplicateTargetsStage implements Stage {

    @Override
    public void run(final MapperContext ctx) {
        final var methodDirectives = ctx.getMethodDirectives();
        if (methodDirectives == null) {
            return;
        }
        methodDirectives.forEach(directives -> validate(directives, ctx));
    }

    void validate(final MethodDirectives directives, final MapperContext ctx) {
        groupByTarget(directives.getBinds()).values().stream()
                .filter(binds -> binds.size() > 1)
                .forEach(binds -> reportDuplicates(binds, ctx));
    }

    Map<String, List<Bind>> groupByTarget(final List<Bind> binds) {
        return binds.stream()
                .collect(
                        groupingBy(bind -> String.join(".", bind.getTargetPath()), HashMap::new, toUnmodifiableList()));
    }

    void reportDuplicates(final List<Bind> binds, final MapperContext ctx) {
        binds.stream()
                .skip(1)
                .forEach(duplicate -> ctx.report(Diagnostic.error(
                                duplicate.getSubject(),
                                "duplicate target '" + String.join(".", duplicate.getTargetPath()) + "'")
                        .asPermanent()));
    }
}
