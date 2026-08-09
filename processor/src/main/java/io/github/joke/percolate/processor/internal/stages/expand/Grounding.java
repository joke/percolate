package io.github.joke.percolate.processor.internal.stages.expand;

import io.github.joke.percolate.spi.Offer;
import io.github.joke.percolate.spi.OperationSpec;
import java.util.List;
import java.util.stream.Stream;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;

import static java.util.stream.Collectors.toUnmodifiableList;

// Grounding-by-match (design D2/D5, change target-driven-engine §§ 2.2–2.4), decomposed (change decompose-
// engine-stages) into an orchestrator over three collaborators: an OperationSpec with no type-variable port
// passes through unchanged; a spec carrying a template port has its match set widened (SourceWidener), every
// consistent binding enumerated (BindingEnumerator, matching each port via the injected Unifier), and one
// fully-concrete spec instantiated per binding (SpecInstantiator). When several sources match, every match is
// instantiated (over-emit); the engine applies no preference and lets cost extraction prune the unreachable
// ones.
@RequiredArgsConstructor
final class Grounding {

    private final SourceWidener widener;
    private final BindingEnumerator enumerator;
    private final SpecInstantiator instantiator;

    // Grounds spec against the sources in scope: a spec with no type-variable port is returned as-is; otherwise one
    // concrete spec is emitted per consistent match (none when nothing unifies — no bridge invented). Every bound
    // refusal encountered along the way (design D6 of change decouple-engine-from-strategy-semantics) is recorded
    // to refusals.
    Stream<OperationSpec> ground(final OperationSpec spec, final List<TypeMirror> sources, final List<Offer> refusals) {
        final var templatePorts = spec.getPorts().stream()
                .filter(port -> port.getTemplate() != null)
                .collect(toUnmodifiableList());
        if (templatePorts.isEmpty()) {
            return Stream.of(spec);
        }
        final var matchSet = widener.widen(sources);
        final var bindingSets = enumerator.enumerate(templatePorts, matchSet, refusals);
        return bindingSets.stream().map(bindings -> instantiator.instantiate(spec, bindings));
    }
}
