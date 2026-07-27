package io.github.joke.percolate.processor.internal.stages.discover;

import io.github.joke.percolate.processor.Diagnostic;
import io.github.joke.percolate.processor.model.Bind;
import io.github.joke.percolate.processor.model.ScopeInputOverride;
import io.github.joke.percolate.spi.Constraint;
import io.github.joke.percolate.spi.DirectiveInput;
import io.github.joke.percolate.spi.DirectiveSink;
import io.github.joke.percolate.spi.Subject;
import io.github.joke.percolate.spi.Visibility;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.VariableElement;

/**
 * The one {@link DirectiveSink} implementation, collecting one mapper method's reader calls as plain data (design D7
 * of change {@code decouple-engine-from-strategy-semantics}): every {@code DirectiveReader} on the processor path
 * runs against the same instance, so declarations from different readers merge naturally — a duplicate target from
 * two different readers is exactly as visible as one from a single reader's own repeated annotation.
 */
@SuppressWarnings("PMD.UseConcurrentHashMap") // single-threaded annotation processing; no concurrent access
final class DirectiveSinkImpl implements DirectiveSink {

    private final List<Bind> binds = new ArrayList<>();
    private final Map<String, List<DirectiveInput>> inputsByTarget = new LinkedHashMap<>();
    private final List<ScopeInputOverride> scopeInputOverrides = new ArrayList<>();
    private final Map<String, List<Constraint>> constraintsByTarget = new LinkedHashMap<>();
    private final List<Diagnostic> rejections = new ArrayList<>();

    @Override
    public void bind(final List<String> targetPath, final List<String> sourcePath, final Subject subject) {
        binds.add(new Bind(List.copyOf(targetPath), List.copyOf(sourcePath), subject));
    }

    @Override
    public void input(final List<String> targetPath, final DirectiveInput input) {
        inputsByTarget
                .computeIfAbsent(String.join(".", targetPath), key -> new ArrayList<>())
                .add(input);
    }

    @Override
    public void scopeInput(final VariableElement parameter, final String name, final Visibility visibility) {
        scopeInputOverrides.add(new ScopeInputOverride(parameter, name, visibility));
    }

    @Override
    public void constrain(final List<String> targetPath, final Constraint constraint) {
        constraintsByTarget
                .computeIfAbsent(String.join(".", targetPath), key -> new ArrayList<>())
                .add(constraint);
    }

    @Override
    public void reject(final Subject subject, final String message) {
        rejections.add(Diagnostic.error(subject, message).asPermanent());
    }

    List<Bind> getBinds() {
        return List.copyOf(binds);
    }

    Map<String, List<DirectiveInput>> getInputsByTarget() {
        return Map.copyOf(inputsByTarget);
    }

    List<ScopeInputOverride> getScopeInputOverrides() {
        return List.copyOf(scopeInputOverrides);
    }

    Map<String, List<Constraint>> getConstraintsByTarget() {
        return Map.copyOf(constraintsByTarget);
    }

    /** What the readers rejected outright, already permanent — reported whether or not anything demands the path. */
    List<Diagnostic> getRejections() {
        return List.copyOf(rejections);
    }
}
