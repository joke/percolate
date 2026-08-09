package io.github.joke.percolate.processor.internal.stages.generate;

import io.github.joke.percolate.lib.javapoet.ClassName;
import io.github.joke.percolate.lib.javapoet.NameAllocator;
import io.github.joke.percolate.lib.javapoet.TypeName;
import io.github.joke.percolate.processor.Diagnostic;
import io.github.joke.percolate.processor.MapperContext;
import io.github.joke.percolate.processor.internal.graph.ExtractedPlan;
import io.github.joke.percolate.processor.internal.graph.MapperGraph;
import io.github.joke.percolate.processor.internal.graph.Operation;
import io.github.joke.percolate.processor.internal.graph.Value;
import io.github.joke.percolate.spi.MemberRequest;
import io.github.joke.percolate.spi.Subjects;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

import static java.util.stream.Collectors.toUnmodifiableList;

// Builds a MemberPlan — the class-wide MemberRequest walk, the dedup-key conflict report, and member naming.
// Split from MemberPlan by change tighten-testability-conventions (design D2), for the same reason as
// HoistPlanFactory: the conflict rule (requests sharing a dedup key must agree on (fieldType, initializer),
// first-seen wins the field) is a decision, and it was unreachable behind a static. It shares
// HoistPlanFactory's reachability walk rather than owning a second copy.
// IdentityHashMap for the reachability walk; LinkedHashMap for deterministic class-scope field-emission order —
// both single-threaded, no concurrent access.
@SuppressWarnings({"PMD.UseConcurrentHashMap", "IdentityHashMapUsage"})
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class MemberPlanFactory {

    private static final int ONE_DEFINITION = 1;

    private final HoistPlanFactory hoistPlanFactory;

    // Builds the member plan for every MemberRequest reachable from any of graph's return roots. Requests sharing a
    // dedup key must agree on (fieldType, initializer) (design D11 of change decouple-engine-from-strategy-
    // semantics); a disagreement is reported at the mapper type and the first-seen request wins the field.
    MemberPlan forMapper(final MapperGraph graph, final ExtractedPlan plan, final MapperContext ctx) {
        final Set<Operation> ops = Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Value> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        graph.returnRoots().forEach(root -> hoistPlanFactory.collectOps(graph, plan, root, ops, seen));

        final Map<String, List<Attribution>> byDedupKey = new LinkedHashMap<>();
        ops.stream().sorted(Comparator.comparing(Operation::id)).forEach(op -> op.getMemberRequests()
                .forEach(request -> byDedupKey
                        .computeIfAbsent(request.getDedupKey(), key -> new ArrayList<>())
                        .add(new Attribution(op.getLabel(), request))));

        byDedupKey.forEach((key, attributions) -> reportConflict(ctx, key, attributions));

        final var names = new NameAllocator();
        final Map<String, String> namesByDedupKey = new LinkedHashMap<>();
        final Map<String, MemberRequest> requestByDedupKey = new LinkedHashMap<>();
        byDedupKey.forEach((key, attributions) -> {
            final var winner = attributions.get(0).getRequest();
            requestByDedupKey.put(key, winner);
            namesByDedupKey.put(key, names.newName(memberBase(winner.getFieldType())));
        });
        return new MemberPlan(namesByDedupKey, requestByDedupKey);
    }

    // Reports a mapper-type-positioned error when attributions disagree on (fieldType, initializer).
    void reportConflict(final MapperContext ctx, final String key, final List<Attribution> attributions) {
        final var distinctRequests =
                attributions.stream().map(Attribution::getRequest).distinct().collect(toUnmodifiableList());
        if (distinctRequests.size() <= ONE_DEFINITION) {
            return;
        }
        final var definitions = distinctRequests.stream()
                .map(request -> request.getFieldType() + " = " + request.getInitializer())
                .collect(Collectors.joining("; "));
        final var operationLabels = attributions.stream()
                .map(Attribution::getOperationLabel)
                .distinct()
                .collect(Collectors.joining(", "));
        ctx.report(Diagnostic.error(
                        Subjects.none(),
                        "conflicting member definitions for '" + key + "': " + definitions + " (requested by "
                                + operationLabels + ")")
                .asPermanent());
    }

    // A lower-camel base name derived from a class field type's simple name, or "member" when unknown.
    String memberBase(final TypeName fieldType) {
        if (!(fieldType instanceof ClassName)) {
            return "member";
        }
        final var simple = ((ClassName) fieldType).simpleName();
        return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }

    // Pairs a MemberRequest with the label of the Operation that requested it.
    @lombok.Value
    private static class Attribution {
        String operationLabel;
        MemberRequest request;
    }
}
