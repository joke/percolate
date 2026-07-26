package io.github.joke.percolate.processor.internal.stages.generate;

import static java.util.stream.Collectors.toUnmodifiableList;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import io.github.joke.percolate.lib.javapoet.ClassName;
import io.github.joke.percolate.lib.javapoet.CodeBlock;
import io.github.joke.percolate.lib.javapoet.FieldSpec;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * The class-scoped sibling of {@link HoistPlan} (design D5 of change {@code add-temporal-type-mapping}): collects
 * every strategy-requested {@link MemberRequest} reachable from any method's winning plan across the <b>whole</b>
 * generated mapper type (not one method — a member may be shared across bodies), deduplicates them by
 * {@link MemberRequest#getDedupKey()}, and names each distinct member via a class-scoped {@link NameAllocator}. A
 * requesting operation's codegen reaches the allocated field's reference through
 * {@link io.github.joke.percolate.spi.IncomingValues#member(String)} — the same indirection a hoisted local reaches
 * its codegen through — so the composer stays field-syntax-free. It mutates neither the {@link MapperGraph} nor the
 * {@link ExtractedPlan}.
 */
// IdentityHashMap for the reachability walk (mirrors HoistPlan.collectOps); LinkedHashMap for deterministic
// class-scope field-emission order — both single-threaded, no concurrent access.
@SuppressWarnings({"PMD.UseConcurrentHashMap", "IdentityHashMapUsage"})
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class MemberPlan {

    private static final int ONE_DEFINITION = 1;

    private final Map<String, String> namesByDedupKey;
    private final Map<String, MemberRequest> requestByDedupKey;

    /**
     * Builds the member plan for every {@link MemberRequest} reachable from any of {@code graph}'s return roots.
     * Requests sharing a dedup key must agree on {@code (fieldType, initializer)} (design D11 of change
     * {@code decouple-engine-from-strategy-semantics}); a disagreement is reported at the mapper type and the
     * first-seen request wins the field.
     */
    static MemberPlan forMapper(final MapperGraph graph, final ExtractedPlan plan, final MapperContext ctx) {
        final Set<Operation> ops = Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Value> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        graph.returnRoots().forEach(root -> HoistPlan.collectOps(graph, plan, root, ops, seen));

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

    /** Reports a mapper-type-positioned error when {@code attributions} disagree on {@code (fieldType, initializer)}. */
    static void reportConflict(final MapperContext ctx, final String key, final List<Attribution> attributions) {
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

    /** Pairs a {@link MemberRequest} with the label of the {@link Operation} that requested it. */
    @lombok.Value
    private static class Attribution {
        String operationLabel;
        MemberRequest request;
    }

    /** The reference to the member registered under {@code dedupKey}. */
    CodeBlock reference(final String dedupKey) {
        final var name = namesByDedupKey.get(dedupKey);
        if (name == null) {
            throw new IllegalStateException("no member registered for dedup key: " + dedupKey);
        }
        return CodeBlock.of("$N", name);
    }

    /** Every distinct requested member as a {@code private static final} field, in allocation order. */
    List<FieldSpec> fields() {
        return namesByDedupKey.entrySet().stream()
                .map(entry -> {
                    final var request = Objects.requireNonNull(requestByDedupKey.get(entry.getKey()));
                    return FieldSpec.builder(request.getFieldType(), entry.getValue(), PRIVATE, STATIC, FINAL)
                            .initializer(request.getInitializer())
                            .build();
                })
                .collect(toUnmodifiableList());
    }

    /** A lower-camel base name derived from a class field type's simple name, or {@code "member"} when unknown. */
    static String memberBase(final TypeName fieldType) {
        if (!(fieldType instanceof ClassName)) {
            return "member";
        }
        final var simple = ((ClassName) fieldType).simpleName();
        return Character.toLowerCase(simple.charAt(0)) + simple.substring(1);
    }
}
