package io.github.joke.percolate.processor.stages.validate;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.processor.Diagnostics;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.model.MapperMappings;
import io.github.joke.percolate.processor.model.MappingDirective;
import io.github.joke.percolate.processor.stages.Stage;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.ExecutableElement;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class ValidateNoDuplicateTargets implements Stage {

    private final Diagnostics diagnostics;

    @Override
    public void run(MapperContext ctx) {
        final var mappings = ctx.getMappings();
        if (mappings == null) {
            return;
        }
        validate(mappings);
    }

    void validate(final MapperMappings mappings) {
        for (final var method : mappings.getMethods()) {
            groupByTarget(method.getDirectives()).entrySet().stream()
                    .filter(e -> e.getValue().size() > 1)
                    .forEach(e -> reportDuplicates(method.getMethod(), e.getKey(), e.getValue()));
        }
    }

    Map<String, List<MappingDirective>> groupByTarget(final List<MappingDirective> directives) {
        return directives.stream().collect(groupingBy(MappingDirective::getTarget, HashMap::new, toUnmodifiableList()));
    }

    void reportDuplicates(
            final ExecutableElement method, final String target, final List<MappingDirective> directives) {
        for (int i = 1; i < directives.size(); i++) {
            final var duplicate = directives.get(i);
            diagnostics.error(
                    method, duplicate.getMirror(), duplicate.getTargetValue(), "duplicate target '" + target + "'");
        }
    }
}
