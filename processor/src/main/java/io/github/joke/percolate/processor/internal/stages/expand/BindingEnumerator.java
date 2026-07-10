package io.github.joke.percolate.processor.internal.stages.expand;

import io.github.joke.percolate.spi.Port;
import io.github.joke.percolate.spi.PortType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.lang.model.type.TypeMirror;
import lombok.RequiredArgsConstructor;

/**
 * Collects every consistent cross-product binding of a spec's template {@link Port}s against a match set (design D4
 * of change {@code decompose-engine-stages}, decomposed out of {@code Grounding}'s {@code assign}): each port is
 * assigned, in turn, to every source that unifies with it — via the injected {@link Unifier} — and each surviving
 * assignment is extended to the next port, so the result is one binding map per fully-consistent choice (the
 * cross-product minus the trials the {@link Unifier} rejects). Its own recursion is over the port index and match
 * set — not self-recursion into its own type structure — so it needs no {@code Spy}, only a mocked {@link Unifier}.
 */
@RequiredArgsConstructor
final class BindingEnumerator {

    private final Unifier unifier;

    /** Every consistent binding map assigning each of {@code ports}' templates to a unifying source in {@code sources}. */
    List<Map<Integer, TypeMirror>> enumerate(final List<Port> ports, final List<TypeMirror> sources) {
        final var out = new ArrayList<Map<Integer, TypeMirror>>();
        assign(ports, 0, sources, new HashMap<>(), out);
        return out;
    }

    /** Assigns {@code ports.get(index)} to each unifying source, recording each consistent binding map once all ports are assigned. */
    void assign(
            final List<Port> ports,
            final int index,
            final List<TypeMirror> sources,
            final Map<Integer, TypeMirror> current,
            final List<Map<Integer, TypeMirror>> out) {
        if (index == ports.size()) {
            out.add(new HashMap<>(current));
            return;
        }
        final var template = Objects.requireNonNull(ports.get(index).getTemplate());
        for (final var source : sources) {
            tryAssign(ports, index, sources, current, out, template, source);
        }
    }

    void tryAssign(
            final List<Port> ports,
            final int index,
            final List<TypeMirror> sources,
            final Map<Integer, TypeMirror> current,
            final List<Map<Integer, TypeMirror>> out,
            final PortType template,
            final TypeMirror source) {
        final var trial = new HashMap<>(current);
        if (unifier.unify(template, source, trial, 0)) {
            assign(ports, index + 1, sources, trial, out);
        }
    }
}
