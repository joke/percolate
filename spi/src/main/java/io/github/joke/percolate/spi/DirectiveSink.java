package io.github.joke.percolate.spi;

import java.util.List;
import javax.lang.model.element.VariableElement;

/**
 * What a {@link DirectiveReader} declares about one mapper method (design D7 of change
 * {@code decouple-engine-from-strategy-semantics}): exactly four entry points, each a neutral structural fact the
 * engine assembles without ever learning the annotation vocabulary behind it.
 *
 * <p>{@link #bind} declares a target binding, optionally pinned to a source path; {@link #input} attaches an
 * author-declared configuration value to a binding, keyed by a name the core does not interpret; {@link #scopeInput}
 * publishes a mapper-method parameter as a named scope input; {@link #constrain} attaches a demand-scoped
 * admissibility {@link Constraint} to a target path (see {@code demand-constraints}).
 *
 * <p>The set of declared children at a target level is <b>derived</b> from the bound target paths, never declared
 * separately, so it cannot disagree with the bindings.
 */
public interface DirectiveSink {

    /** Declares a target binding at {@code targetPath}, optionally pinned to {@code sourcePath} (empty for none). */
    void bind(List<String> targetPath, List<String> sourcePath, Subject subject);

    /** Attaches {@code input} — scalar or structured — to the binding at {@code targetPath}. */
    void input(List<String> targetPath, DirectiveInput input);

    /** Publishes {@code parameter} as a named scope input with the declared {@code visibility}. */
    void scopeInput(VariableElement parameter, String name, Visibility visibility);

    /** Attaches a demand-scoped admissibility {@code constraint} to {@code targetPath}. */
    void constrain(List<String> targetPath, Constraint constraint);
}
