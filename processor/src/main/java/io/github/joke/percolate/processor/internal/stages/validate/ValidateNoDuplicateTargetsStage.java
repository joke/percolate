package io.github.joke.percolate.processor.internal.stages.validate;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.Diagnostic;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.internal.stages.Stage;
import io.github.joke.percolate.processor.model.MapperMappings;
import io.github.joke.percolate.processor.model.MappingDirective;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class ValidateNoDuplicateTargetsStage implements Stage {

    @Override
    public void run(final MapperContext ctx) {
        final var mappings = ctx.getMappings();
        if (mappings == null) {
            return;
        }
        validate(mappings, ctx);
    }

    void validate(final MapperMappings mappings, final MapperContext ctx) {
        for (final var method : mappings.getMethods()) {
            groupByTarget(method.getDirectives()).entrySet().stream()
                    .filter(e -> e.getValue().size() > 1)
                    .forEach(e -> reportDuplicates(e.getKey(), e.getValue(), ctx));
        }
    }

    Map<String, List<MappingDirective>> groupByTarget(final List<MappingDirective> directives) {
        return directives.stream().collect(groupingBy(MappingDirective::getTarget, HashMap::new, toUnmodifiableList()));
    }

    void reportDuplicates(final String target, final List<MappingDirective> directives, final MapperContext ctx) {
        directives.stream()
                .skip(1)
                .forEach(duplicate ->
                        ctx.report(Diagnostic.error(duplicate.getTargetSubject(), "duplicate target '" + target + "'")
                                .asPermanent()));
    }
}
