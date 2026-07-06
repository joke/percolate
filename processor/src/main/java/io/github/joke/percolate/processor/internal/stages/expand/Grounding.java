package io.github.joke.percolate.processor.internal.stages.expand;

import static java.util.stream.Collectors.toUnmodifiableList;

import io.github.joke.percolate.spi.OperationSpec;
import java.util.List;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;

/**
 * Grounding-by-match (design D2/D5, change {@code target-driven-engine} §§ 2.2–2.4), decomposed (change
 * {@code decompose-engine-stages}) into an orchestrator over three collaborators: an {@link OperationSpec} with no
 * type-variable port passes through unchanged; a spec carrying a template port has its match set <b>widened</b>
 * ({@link SourceWidener}), every consistent binding <b>enumerated</b> ({@link BindingEnumerator}, matching each port
 * via the injected {@link Unifier}), and one fully-concrete spec <b>instantiated</b> per binding
 * ({@link SpecInstantiator}). When several sources match, every match is instantiated (over-emit); the engine applies
 * no preference and lets cost extraction prune the unreachable ones.
 */
@RequiredArgsConstructor
final class Grounding {

    private final SourceWidener widener;
    private final BindingEnumerator enumerator;
    private final SpecInstantiator instantiator;

    /**
     * Grounds {@code spec} against the {@code sources} in scope: a spec with no type-variable port is returned as-is;
     * otherwise one concrete spec is emitted per consistent match (none when nothing unifies — no bridge invented).
     */
    Stream<OperationSpec> ground(final OperationSpec spec, final List<TypeMirror> sources) {
        final var templatePorts = spec.getPorts().stream()
                .filter(port -> port.getTemplate() != null)
                .collect(toUnmodifiableList());
        if (templatePorts.isEmpty()) {
            return Stream.of(spec);
        }
        final var matchSet = widener.widen(sources);
        final var bindingSets = enumerator.enumerate(templatePorts, matchSet);
        return bindingSets.stream().map(bindings -> instantiator.instantiate(spec, bindings));
    }
}
