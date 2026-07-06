package io.github.joke.percolate.processor.internal.stages.expand;

import io.github.joke.percolate.processor.internal.graph.Value;
import io.github.joke.percolate.processor.model.MapperShape;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;

/**
 * Drives the demand work-list to fixpoint (design D6 of change {@code target-driven-engine}, decomposed out of
 * {@code ExpandStage.Driver} by {@code decompose-engine-stages}): self-seeds one return-root demand per abstract
 * method, then repeatedly pops a demand and, the first time it is visited, hands it to the injected
 * {@link Expander} — which may enqueue further demands through the callback it is given. Graph-agnostic: it holds
 * no expansion-specific logic itself, only the fixpoint mechanics.
 */
@RequiredArgsConstructor
final class ExpansionLoop {

    private final Seeder seeder;
    private final Expander expander;
    private final Deque<Value> workList = new ArrayDeque<>();
    private final Set<Value> visited = new HashSet<>();

    /** Self-seeds one return-root demand per abstract method into the empty graph, then drains the work-list. */
    void seedAndExpand(final MapperShape shape) {
        shape.getAbstractMethods().forEach(method -> enqueue(seeder.seed(method)));
        while (!workList.isEmpty()) {
            final var value = workList.poll();
            if (visited.add(value)) {
                expander.expand(value, this::enqueue);
            }
        }
    }

    /** Adds {@code value} to the work-list, to be expanded once (first visit only) when its turn comes. */
    void enqueue(final Value value) {
        workList.add(value);
    }

    /** One step of expansion: process {@code value}, enqueueing any further demand it admits through {@code enqueue}. */
    @FunctionalInterface
    interface Expander {
        void expand(Value value, Consumer<Value> enqueue);
    }
}
